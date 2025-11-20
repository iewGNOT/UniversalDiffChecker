package com.universaldiff.core;

import com.universaldiff.core.model.ComparisonOptions;
import com.universaldiff.core.model.ComparisonSession;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void compare_txtProducesExpectedHunksAndMerge() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.txt"), "a\nb\nc\n", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.txt"), "a\nx\nc\nz\n", StandardCharsets.UTF_8);
        Path output = tempDir.resolve("merged.txt");

        ComparisonService service = ComparisonService.createDefault();
        ComparisonSession session = service.compare(left, right);

        assertThat(session.getDiffResult().getHunks())
                .extracting(DiffHunk::getId)
                .containsExactlyInAnyOrder("txt-line-2", "txt-line-4");

        List<MergeDecision> decisions = session.getDiffResult().getHunks().stream()
                .map(h -> new MergeDecision(h.getId(), MergeChoice.TAKE_RIGHT, null))
                .collect(Collectors.toList());

        MergeResult merged = session.merge(decisions, output);

        assertThat(merged.getOutputPath()).isEqualTo(output);
        assertThat(Files.readAllLines(output))
                .containsExactly("a", "x", "c", "z");
    }

    @Test
    void compare_forcedFormatStillDiffsAsPlainText() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.data"), "{\"a\":1}", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.data"), "{\"a\":2}", StandardCharsets.UTF_8);

        ComparisonService service = ComparisonService.createDefault();
        ComparisonOptions options = ComparisonOptions.builder()
                .forcedFormat(FormatType.JSON)
                .build();

        ComparisonSession session = service.compare(left, right, options);

        assertThat(session.getDiffResult().getHunks()).hasSize(1);
        assertThat(session.getDiffResult().getFormatType()).isEqualTo(FormatType.TXT);
    }
}
