package com.universaldiff.ui.viewmodel;

import com.universaldiff.core.model.DiffHunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DiffViewModelTest {

    @TempDir
    Path tempDir;

    @Test
    void comparePublishesHunksAndSession() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.txt"), "a\nb\nc\n", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.txt"), "a\nx\nc\nz\n", StandardCharsets.UTF_8);

        DiffViewModel viewModel = new DiffViewModel();
        viewModel.leftPathProperty().set(left);
        viewModel.rightPathProperty().set(right);

        viewModel.compare();

        assertThat(viewModel.getCurrentSession()).isPresent();
        assertThat(viewModel.hunksProperty())
                .extracting(DiffHunk::getId)
                .containsExactlyInAnyOrder("txt-line-2", "txt-line-4");
    }
}
