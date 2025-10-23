package com.universaldiff.format.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.DiffType;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonMergeManualTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void merge_manualInvalidJsonRaisesIOException() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.json"), "{\"value\":1}", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.json"), "{\"value\":2}", StandardCharsets.UTF_8);

        JsonFormatAdapter adapter = new JsonFormatAdapter(false);
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.JSON, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.JSON, StandardCharsets.UTF_8));

        DiffResult diff = adapter.diff(leftContent, rightContent);
        MergeDecision decision = new MergeDecision(diff.getHunks().get(0).getId(), MergeChoice.MANUAL, "{not-json}");

        assertThatThrownBy(() -> adapter.merge(leftContent, rightContent, List.of(decision), tempDir.resolve("out.json")))
                .isInstanceOf(IOException.class);
    }

    @Test
    void merge_manualValidJsonUpdatesLeafAndObject() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.json"),
                """
                        {
                          "root": {
                            "value": 1,
                            "nested": { "inner": 10 }
                          },
                          "array": [1,2,3]
                        }
                        """, StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.json"),
                """
                        {
                          "root": {
                            "value": 2,
                            "nested": { "inner": 20 }
                          },
                          "array": [1,3,2]
                        }
                        """, StandardCharsets.UTF_8);
        Path output = tempDir.resolve("merged.json");

        JsonFormatAdapter adapter = new JsonFormatAdapter(false);
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.JSON, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.JSON, StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        List<MergeDecision> decisions = diff.getHunks().stream()
                .map(h -> {
                    String pointer = decodePointer(h.getId());
                    if ("/root/nested/inner".equals(pointer)) {
                        return new MergeDecision(h.getId(), MergeChoice.MANUAL, "42");
                    }
                    if ("/root/value".equals(pointer)) {
                        return new MergeDecision(h.getId(), MergeChoice.MANUAL, "5");
                    }
                    return new MergeDecision(h.getId(), MergeChoice.TAKE_RIGHT, null);
                })
                .toList();

        MergeResult result = adapter.merge(leftContent, rightContent, decisions, output);
        assertThat(result.getFormatType()).isEqualTo(FormatType.JSON);

        JsonNode merged = MAPPER.readTree(Files.readString(output, StandardCharsets.UTF_8));
        assertThat(merged.at("/root/value").asInt()).isEqualTo(5);
        assertThat(merged.at("/root/nested/inner").asInt()).isEqualTo(42);
        assertThat(merged.at("/array")).isEqualTo(MAPPER.readTree("[1,3,2]"));
    }

    @Test
    void diff_detectsArrayAndNestedObjectChanges() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.json"),
                """
                        {
                          "items": [
                            {"id":1,"values":[1,2]},
                            {"id":2,"values":[3,4]}
                          ]
                        }
                        """, StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.json"),
                """
                        {
                          "items": [
                            {"id":2,"values":[3,5]},
                            {"id":1,"values":[1,2]}
                          ]
                        }
                        """, StandardCharsets.UTF_8);

        JsonFormatAdapter adapter = new JsonFormatAdapter(false);
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.JSON, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.JSON, StandardCharsets.UTF_8));

        DiffResult diff = adapter.diff(leftContent, rightContent);

        assertThat(diff.getHunks())
                .extracting(DiffHunk::getType)
                .contains(DiffType.MODIFY);

        assertThat(diff.getHunks())
                .extracting(h -> decodePointer(h.getId()))
                .anyMatch(pointer -> pointer.contains("/items/0"))
                .anyMatch(pointer -> pointer.contains("/items/1"));
    }

    @Test
    void diff_respectsIgnoreKeyOrderToggle() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.json"),
                """
                        {
                          "outer": {
                            "first": {"a":1,"b":2},
                            "second": {"x":true,"y":false}
                          }
                        }
                        """, StandardCharsets.UTF_8);
        Path rightOrderOnly = Files.writeString(tempDir.resolve("right-order.json"),
                """
                        {
                          "outer": {
                            "first": {"b":2,"a":1},
                            "second": {"y":false,"x":true}
                          }
                        }
                        """, StandardCharsets.UTF_8);

        JsonFormatAdapter ignoring = new JsonFormatAdapter(true);
        DiffResult ignoredDiff = ignoring.diff(
                ignoring.normalize(new FileDescriptor(left, FormatType.JSON, StandardCharsets.UTF_8)),
                ignoring.normalize(new FileDescriptor(rightOrderOnly, FormatType.JSON, StandardCharsets.UTF_8))
        );
        assertThat(ignoredDiff.getHunks()).isEmpty();

        Path rightWithChange = Files.writeString(tempDir.resolve("right-change.json"),
                """
                        {
                          "outer": {
                            "first": {"b":2,"a":1},
                            "second": {"y":false,"x":true,"extra":1}
                          }
                        }
                        """, StandardCharsets.UTF_8);

        JsonFormatAdapter strict = new JsonFormatAdapter(false);
        DiffResult strictDiff = strict.diff(
                strict.normalize(new FileDescriptor(left, FormatType.JSON, StandardCharsets.UTF_8)),
                strict.normalize(new FileDescriptor(rightWithChange, FormatType.JSON, StandardCharsets.UTF_8))
        );
        assertThat(strictDiff.getHunks()).isNotEmpty();
    }

    private String decodePointer(String hunkId) {
        String encoded = hunkId.replace("json-path-", "");
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}



