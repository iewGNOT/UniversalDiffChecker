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
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonFormatAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void diff_ignoresKeyOrderWhenConfigured() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.json"), "{\"a\":1,\"b\":2}", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.json"), "{\"b\":2,\"a\":1}", StandardCharsets.UTF_8);

        JsonFormatAdapter adapter = new JsonFormatAdapter(true);
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.JSON, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.JSON, StandardCharsets.UTF_8));

        DiffResult diff = adapter.diff(leftContent, rightContent);

        assertThat(diff.getHunks()).isEmpty();
    }

    @Test
    void diff_reportsValueChange() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.json"), "{\"a\":1}", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.json"), "{\"a\":2}", StandardCharsets.UTF_8);

        JsonFormatAdapter adapter = new JsonFormatAdapter(false);
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.JSON, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.JSON, StandardCharsets.UTF_8));

        DiffResult diff = adapter.diff(leftContent, rightContent);

        assertThat(diff.getHunks()).hasSize(1);
        DiffHunk hunk = diff.getHunks().get(0);
        assertThat(hunk.getId()).startsWith("json-path-");
        assertThat(hunk.getType()).isEqualTo(DiffType.MODIFY);
    }

    @Test
    void merge_takeRightAppliesChanges() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.json"), "{\"a\":1}", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.json"), "{\"a\":2}", StandardCharsets.UTF_8);
        Path output = tempDir.resolve("merged.json");

        JsonFormatAdapter adapter = new JsonFormatAdapter(false);
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.JSON, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.JSON, StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        List<MergeDecision> decisions = diff.getHunks().stream()
                .map(h -> new MergeDecision(h.getId(), MergeChoice.TAKE_RIGHT, null))
                .collect(Collectors.toList());

        MergeResult result = adapter.merge(leftContent, rightContent, decisions, output);

        assertThat(result.getOutputPath()).isEqualTo(output);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode mergedNode = mapper.readTree(Files.readString(output, StandardCharsets.UTF_8));
        assertThat(mergedNode).isEqualTo(mapper.readTree("{\"a\":2}"));
    }

    @Test
    void normalize_invalidJsonThrowsIOException() throws Exception {
        Path invalid = Files.writeString(tempDir.resolve("bad.json"), "{this is not json}", StandardCharsets.UTF_8);
        JsonFormatAdapter adapter = new JsonFormatAdapter();

        assertThatThrownBy(() ->
                adapter.normalize(new FileDescriptor(invalid, FormatType.JSON, StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class);
    }
}
