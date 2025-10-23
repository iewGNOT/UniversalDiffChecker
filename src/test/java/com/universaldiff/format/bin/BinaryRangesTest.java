package com.universaldiff.format.bin;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class BinaryRangesTest {

    @TempDir
    Path tempDir;

    @Test
    void diff_detectsMultipleNonOverlappingRanges() throws Exception {
        Path left = tempDir.resolve("left.bin");
        Path right = tempDir.resolve("right.bin");
        Files.write(left, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});
        Files.write(right, new byte[]{0x01, (byte) 0xFF, 0x03, 0x04, (byte) 0xAA});

        BinaryFormatAdapter adapter = new BinaryFormatAdapter(FormatType.BIN);
        DiffResult diff = adapter.diff(
                adapter.normalize(new FileDescriptor(left, FormatType.BIN, StandardCharsets.UTF_8)),
                adapter.normalize(new FileDescriptor(right, FormatType.BIN, StandardCharsets.UTF_8))
        );

        assertThat(diff.getHunks())
                .extracting(DiffHunk::getId, DiffHunk::getType)
                .containsExactlyInAnyOrder(
                        tuple("bin-offset-0x1-len-1", DiffType.MODIFY),
                        tuple("bin-offset-0x4-len-1", DiffType.MODIFY)
                );
    }

    @Test
    void merge_takeRightAppliesAllRanges() throws Exception {
        Path left = tempDir.resolve("left.bin");
        Path right = tempDir.resolve("right.bin");
        Path output = tempDir.resolve("merged.bin");
        Files.write(left, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});
        Files.write(right, new byte[]{0x01, (byte) 0xFF, 0x03, 0x04, (byte) 0xAA});

        BinaryFormatAdapter adapter = new BinaryFormatAdapter(FormatType.BIN);
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.BIN, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.BIN, StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        List<MergeDecision> decisions = diff.getHunks().stream()
                .map(h -> new MergeDecision(h.getId(), MergeChoice.TAKE_RIGHT, null))
                .toList();

        MergeResult result = adapter.merge(leftContent, rightContent, decisions, output);
        assertThat(result.getOutputPath()).isEqualTo(output);
        assertThat(Files.readAllBytes(output)).containsExactly(Files.readAllBytes(right));
    }

    @Test
    void merge_manualInvalidHexThrows() throws Exception {
        Path left = tempDir.resolve("left.bin");
        Path right = tempDir.resolve("right.bin");
        Files.write(left, new byte[]{0x01, 0x02});
        Files.write(right, new byte[]{0x01, (byte) 0xFF});

        BinaryFormatAdapter adapter = new BinaryFormatAdapter(FormatType.BIN);
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.BIN, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.BIN, StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);
        String hunkId = diff.getHunks().get(0).getId();

        MergeDecision decision = new MergeDecision(hunkId, MergeChoice.MANUAL, "F");
        assertThatThrownBy(() -> adapter.merge(leftContent, rightContent, List.of(decision), tempDir.resolve("out.bin")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalize_hexInputProducesExpectedBytes() throws Exception {
        Path hexFile = tempDir.resolve("data.hex");
        Files.writeString(hexFile, "41 42 43 0a", StandardCharsets.UTF_8);

        BinaryFormatAdapter adapter = new BinaryFormatAdapter(FormatType.HEX);
        NormalizedContent content = adapter.normalize(new FileDescriptor(hexFile, FormatType.HEX, StandardCharsets.UTF_8));

        assertThat(content.getBinary()).containsExactly('A', 'B', 'C', '\n');
    }
}
