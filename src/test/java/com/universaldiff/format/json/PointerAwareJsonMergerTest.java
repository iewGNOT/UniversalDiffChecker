package com.universaldiff.format.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointerAwareJsonMergerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void mergeAppliesRightValuesAndGrowsArrays() throws Exception {
        NormalizedContent left = content("""
                {"items":[{"value":1}],"meta":{"count":1}}
                """);
        NormalizedContent right = content("""
                {"items":[{"value":2},{"value":3}],"meta":{"count":2}}
                """);

        List<MergeDecision> decisions = List.of(
                decision("/items/0/value", MergeChoice.TAKE_RIGHT, null),
                decision("/items/1", MergeChoice.TAKE_RIGHT, null),
                decision("/meta/count", MergeChoice.MANUAL, "5")
        );

        Path output = tempDir.resolve("merged.json");
        MergeResult result = new PointerAwareJsonMerger(mapper).merge(left, right, decisions, output);

        assertThat(result.getFormatType()).isEqualTo(FormatType.JSON);
        JsonNode merged = mapper.readTree(Files.readString(output));
        assertThat(merged.at("/items/0/value").asInt()).isEqualTo(2);
        assertThat(merged.at("/items/1/value").asInt()).isEqualTo(3);
        assertThat(merged.at("/meta/count").asInt()).isEqualTo(5);
    }

    @Test
    void mergeRejectsRootReplacement() throws Exception {
        NormalizedContent content = content("{\"root\":1}");
        PointerAwareJsonMerger merger = new PointerAwareJsonMerger(mapper);

        assertThatThrownBy(() ->
                merger.merge(content, content,
                        List.of(decision("", MergeChoice.TAKE_RIGHT, null)), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mergeFailsWhenPointerTraversesScalar() throws Exception {
        NormalizedContent content = content("{\"value\":1}");
        PointerAwareJsonMerger merger = new PointerAwareJsonMerger(mapper);

        assertThatThrownBy(() ->
                merger.merge(content, content,
                        List.of(decision("/value/child", MergeChoice.TAKE_RIGHT, null)), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private NormalizedContent content(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        return NormalizedContent.builder(FormatType.JSON)
                .nativeModel(node)
                .encoding(StandardCharsets.UTF_8)
                .build();
    }

    private MergeDecision decision(String pointer, MergeChoice choice, String manual) {
        String encoded = Base64.getEncoder().encodeToString(pointer.getBytes(StandardCharsets.UTF_8));
        return new MergeDecision("json-path-" + encoded, choice, manual);
    }
}
