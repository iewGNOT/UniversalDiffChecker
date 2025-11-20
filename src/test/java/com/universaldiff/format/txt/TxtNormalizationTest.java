package com.universaldiff.format.txt;

import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.NormalizedContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TxtNormalizationTest {

    @TempDir
    Path tempDir;

    @Test
    void diff_treatsDifferentLineEndingsAsEqual() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.txt"), "a\r\nb\r\nc\r\n", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.txt"), "a\nb\nc\n", StandardCharsets.UTF_8);

        TxtFormatAdapter adapter = new TxtFormatAdapter();
        DiffResult diff = adapter.diff(
                adapter.normalize(new FileDescriptor(left, FormatType.TXT, StandardCharsets.UTF_8)),
                adapter.normalize(new FileDescriptor(right, FormatType.TXT, StandardCharsets.UTF_8))
        );

        assertThat(diff.getHunks()).isEmpty();
    }

    @Test
    void diff_noLongerNormalizesTabsToSpaces() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.txt"), "column1\tcolumn2\n", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.txt"), "column1    column2\n", StandardCharsets.UTF_8);

        TxtFormatAdapter adapter = new TxtFormatAdapter();
        DiffResult diff = adapter.diff(
                adapter.normalize(new FileDescriptor(left, FormatType.TXT, StandardCharsets.UTF_8)),
                adapter.normalize(new FileDescriptor(right, FormatType.TXT, StandardCharsets.UTF_8))
        );

        assertThat(diff.getHunks()).hasSize(1);
    }

    @Test
    void diff_handlesEmptyAndSingleLineFiles() throws Exception {
        Path empty = Files.writeString(tempDir.resolve("empty.txt"), "", StandardCharsets.UTF_8);
        Path single = Files.writeString(tempDir.resolve("single.txt"), "only line\n", StandardCharsets.UTF_8);

        TxtFormatAdapter adapter = new TxtFormatAdapter();
        NormalizedContent emptyContent = adapter.normalize(new FileDescriptor(empty, FormatType.TXT, StandardCharsets.UTF_8));
        NormalizedContent singleContent = adapter.normalize(new FileDescriptor(single, FormatType.TXT, StandardCharsets.UTF_8));

        assertThat(emptyContent.getLogicalRecords()).isEmpty();
        assertThat(singleContent.getLogicalRecords()).containsExactly("only line");

        DiffResult diff = adapter.diff(emptyContent, singleContent);
        assertThat(diff.getHunks())
                .extracting(h -> h.getId(), h -> h.getType())
                .containsExactly(org.assertj.core.api.Assertions.tuple("txt-line-1", com.universaldiff.core.model.DiffType.INSERT));
    }
}
