package com.universaldiff.ui.viewmodel;

import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        // 使用同步版本，调用结束时结果已经准备好
        viewModel.compareBlockingForTest();

        assertThat(viewModel.getCurrentSession()).isPresent();
        assertThat(viewModel.hunksProperty())
                .extracting(DiffHunk::getId)
                .containsExactlyInAnyOrder("txt-line-2", "txt-line-4");
    }

    @Test
    void compareBlockingRequiresBothSides() {
        DiffViewModel viewModel = new DiffViewModel();
        viewModel.leftPathProperty().set(tempDir.resolve("missing-left.txt"));

        assertThatThrownBy(viewModel::compareBlockingForTest)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Both files must be selected");
    }

    @Test
    void compareBlockingFailsWhenFileDoesNotExist() {
        DiffViewModel viewModel = new DiffViewModel();
        Path left = tempDir.resolve("left.txt");
        Path right = tempDir.resolve("right.txt");
        viewModel.leftPathProperty().set(left);
        viewModel.rightPathProperty().set(right);

        assertThatThrownBy(viewModel::compareBlockingForTest)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void mergeRequiresActiveSession() {
        DiffViewModel viewModel = new DiffViewModel();

        assertThatThrownBy(() ->
                viewModel.merge(List.of(new MergeDecision("txt-line-1", MergeChoice.TAKE_LEFT, null)),
                        tempDir.resolve("out.txt")))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("No active comparison session");
    }

    @Test
    void mergeWritesOutputWhenSessionExists() throws Exception {
        Path left = Files.writeString(tempDir.resolve("merge-left.txt"), "keep\nleft\n", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("merge-right.txt"), "keep\nright\n", StandardCharsets.UTF_8);
        Path output = tempDir.resolve("merged.txt");

        DiffViewModel viewModel = new DiffViewModel();
        viewModel.leftPathProperty().set(left);
        viewModel.rightPathProperty().set(right);
        viewModel.compareBlockingForTest();

        MergeResult result = viewModel.merge(List.of(
                new MergeDecision("txt-line-2", MergeChoice.TAKE_RIGHT, null)
        ), output);

        assertThat(result.getOutputPath()).isEqualTo(output);
        assertThat(Files.readAllLines(output, StandardCharsets.UTF_8))
                .containsExactly("keep", "right");
    }

    @Test
    void readFilePreviewReturnsLines() throws Exception {
        Path previewFile = Files.writeString(tempDir.resolve("preview.txt"), "a\nb\n", StandardCharsets.UTF_8);
        DiffViewModel viewModel = new DiffViewModel();

        assertThat(viewModel.readFilePreview(previewFile))
                .containsExactly("a", "b");
    }
}



