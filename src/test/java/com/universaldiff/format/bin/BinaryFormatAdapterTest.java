package com.universaldiff.format.bin;

import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.DiffType;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BinaryFormatAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void diff_detectsByteRangeDifferences() throws Exception {
        Path left = tempDir.resolve("left.bin");
        Path right = tempDir.resolve("right.bin");
        Files.write(left, new byte[]{0x01, 0x02, 0x03});
        Files.write(right, new byte[]{0x01, (byte) 0xFF, 0x03});

        BinaryFormatAdapter adapter = new BinaryFormatAdapter(FormatType.BIN);
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.BIN, java.nio.charset.StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.BIN, java.nio.charset.StandardCharsets.UTF_8));

        DiffResult diff = adapter.diff(leftContent, rightContent);

        assertThat(diff.getHunks()).hasSize(1);
        DiffHunk first = diff.getHunks().get(0);
        assertThat(first.getId()).isEqualTo("bin-offset-0x1-len-1");
        assertThat(first.getType()).isEqualTo(DiffType.MODIFY);
    }

    @Test
    void merge_withoutDecisionsKeepsLeftBytes() throws Exception {
        Path left = tempDir.resolve("left.bin");
        Path right = tempDir.resolve("right.bin");
        Path output = tempDir.resolve("merged.bin");
        Files.write(left, new byte[]{0x01, 0x02, 0x03});
        Files.write(right, new byte[]{0x01, (byte) 0xFF, 0x03});

        BinaryFormatAdapter adapter = new BinaryFormatAdapter(FormatType.BIN);
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.BIN, java.nio.charset.StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.BIN, java.nio.charset.StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        List<MergeDecision> decisions = List.of();

        MergeResult result = adapter.merge(leftContent, rightContent, decisions, output);

        assertThat(result.getOutputPath()).isEqualTo(output);
        assertThat(Files.readAllBytes(output)).containsExactly(0x01, 0x02, 0x03);
    }

    @Test
    void merge_takeRightCopiesRightBytes() throws Exception {
        Path left = tempDir.resolve("left.bin");
        Path right = tempDir.resolve("right.bin");
        Path output = tempDir.resolve("merged.bin");
        Files.write(left, new byte[]{0x01, 0x02, 0x03});
        Files.write(right, new byte[]{0x01, (byte) 0xFF, 0x03, 0x04});

        BinaryFormatAdapter adapter = new BinaryFormatAdapter(FormatType.BIN);
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.BIN, java.nio.charset.StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.BIN, java.nio.charset.StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        List<MergeDecision> decisions = diff.getHunks().stream()
                .map(h -> new MergeDecision(h.getId(), MergeChoice.TAKE_RIGHT, null))
                .toList();

        assertThat(decisions).isNotEmpty();

        MergeResult result = adapter.merge(leftContent, rightContent, decisions, output);

        assertThat(result.getOutputPath()).isEqualTo(output);
        assertThat(Files.readAllBytes(output)).containsExactly(Files.readAllBytes(right));
    }
}
