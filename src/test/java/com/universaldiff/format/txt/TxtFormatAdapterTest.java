package com.universaldiff.format.txt;

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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TxtFormatAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void diff_lineModifyAndInsert() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.txt"), "a\nb\nc\n", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.txt"), "a\nx\nc\nz\n", StandardCharsets.UTF_8);

        TxtFormatAdapter adapter = new TxtFormatAdapter();
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.TXT, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.TXT, StandardCharsets.UTF_8));

        DiffResult diff = adapter.diff(leftContent, rightContent);

        assertThat(diff.getHunks())
                .extracting(DiffHunk::getId)
                .containsExactlyInAnyOrder("txt-line-2", "txt-line-4");

        assertThat(diff.getHunks().stream()
                .collect(Collectors.toMap(DiffHunk::getId, DiffHunk::getType)))
                .containsEntry("txt-line-2", DiffType.MODIFY)
                .containsEntry("txt-line-4", DiffType.INSERT);
    }

    @Test
    void merge_takeRightProducesRightFile() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.txt"), "a\nb\nc\n", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.txt"), "a\nx\nc\nz\n", StandardCharsets.UTF_8);
        Path output = tempDir.resolve("merged.txt");

        TxtFormatAdapter adapter = new TxtFormatAdapter();
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.TXT, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.TXT, StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        List<MergeDecision> decisions = diff.getHunks().stream()
                .map(h -> new MergeDecision(h.getId(), MergeChoice.TAKE_RIGHT, null))
                .collect(Collectors.toList());

        MergeResult result = adapter.merge(leftContent, rightContent, decisions, output);

        assertThat(result.getOutputPath()).isEqualTo(output);
        assertThat(Files.readAllLines(output))
                .containsExactlyElementsOf(Files.readAllLines(right));
    }

    @Test
    void diff_reportsDeletesWhenRightIsShorter() {
        TxtFormatAdapter adapter = new TxtFormatAdapter();
        NormalizedContent left = NormalizedContent.builder(FormatType.TXT)
                .logicalRecords(List.of("one", "two"))
                .build();
        NormalizedContent right = NormalizedContent.builder(FormatType.TXT)
                .logicalRecords(List.of("one"))
                .build();

        DiffResult diff = adapter.diff(left, right);

        assertThat(diff.getHunks())
                .extracting(DiffHunk::getType)
                .contains(DiffType.DELETE);
    }

    @Test
    void merge_appliesManualEditsAndIgnoresInvalidIds() throws Exception {
        TxtFormatAdapter adapter = new TxtFormatAdapter();
        NormalizedContent left = NormalizedContent.builder(FormatType.TXT)
                .logicalRecords(List.of("L1", "L2"))
                .encoding(StandardCharsets.UTF_8)
                .build();
        NormalizedContent right = NormalizedContent.builder(FormatType.TXT)
                .logicalRecords(List.of("R1", "R2", "R3"))
                .encoding(StandardCharsets.UTF_8)
                .build();

        List<MergeDecision> decisions = List.of(
                new MergeDecision("txt-line-1", MergeChoice.TAKE_RIGHT, null),
                new MergeDecision("txt-line-3", MergeChoice.MANUAL, "manual"),
                new MergeDecision("bad-id", MergeChoice.MANUAL, "ignored")
        );

        Path output = tempDir.resolve("manual.txt");
        adapter.merge(left, right, decisions, output);

        assertThat(Files.readAllLines(output, StandardCharsets.UTF_8))
                .containsExactly("R1", "L2", "manual");
    }
}
