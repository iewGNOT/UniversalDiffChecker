package com.universaldiff.core;

import com.universaldiff.core.model.ComparisonOptions;
import com.universaldiff.core.model.ComparisonSession;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComparisonServiceErrorTest {

    @TempDir
    Path tempDir;

    @Test
    void compare_mismatchedFormatsWithoutOverrideThrows() throws Exception {
        Path json = Files.writeString(tempDir.resolve("value.json"), "{\"a\":1}", StandardCharsets.UTF_8);
        Path text = Files.writeString(tempDir.resolve("value.txt"), "plain text", StandardCharsets.UTF_8);

        ComparisonService service = ComparisonService.createDefault();

        assertThatThrownBy(() -> service.compare(json, text))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("JSON")
                .hasMessageContaining("TXT");
    }

    @Test
    void compare_unknownFormatSuggestsOverride() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("dir"));
        Path other = Files.writeString(tempDir.resolve("other.txt"), "content", StandardCharsets.UTF_8);

        ComparisonService service = ComparisonService.createDefault();

        assertThatThrownBy(() -> service.compare(directory, other))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Unable to detect file format");
    }

    @Test
    void compare_forceTxtOnJsonStillProducesDiff() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.json"), "{\"a\":1}", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.json"), "{\"a\":2}", StandardCharsets.UTF_8);

        ComparisonService service = ComparisonService.createDefault();
        ComparisonOptions options = ComparisonOptions.builder()
                .forcedFormat(FormatType.TXT)
                .build();

        ComparisonSession session = service.compare(left, right, options);
        assertThat(session.getDiffResult().getHunks()).isNotEmpty();
    }

    @Test
    void merge_preservesLeftEncoding() throws Exception {
        Path left = tempDir.resolve("left.txt");
        Path right = tempDir.resolve("right.txt");
        // UTF-16LE BOM + content
        Files.write(left, new byte[]{(byte) 0xFF, (byte) 0xFE, 'a', 0, '\n', 0});
        Files.write(right, new byte[]{(byte) 0xFF, (byte) 0xFE, 'b', 0, '\n', 0});
        Path output = tempDir.resolve("merged.txt");

        ComparisonService service = ComparisonService.createDefault();
        ComparisonSession session = service.compare(left, right);

        List<MergeDecision> decisions = session.getDiffResult().getHunks().stream()
                .map(h -> new MergeDecision(h.getId(), MergeChoice.TAKE_RIGHT, null))
                .toList();
        MergeResult result = session.merge(decisions, output);

        assertThat(result.getOutputPath()).isEqualTo(output);
        byte[] bytes = Files.readAllBytes(output);
        assertThat(bytes).startsWith(new byte[]{(byte) 0xFF, (byte) 0xFE});
    }
}
