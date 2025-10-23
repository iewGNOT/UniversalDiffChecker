package com.universaldiff.ui.viewmodel;

import com.universaldiff.core.model.DiffHunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiffViewModelJsonOrderTest {

    @TempDir
    Path tempDir;

    @Test
    void jsonKeyOrderToggleControlsDiffHunks() throws Exception {
        String leftJson = "{\"outer\":{\"first\":{\"a\":1,\"b\":2},\"second\":{\"x\":true,\"y\":false}}}";
        String rightJsonOrder = "{\"outer\":{\"first\":{\"b\":2,\"a\":1},\"second\":{\"y\":false,\"x\":true}}}";
        Path left = Files.writeString(tempDir.resolve("left.json"), leftJson, StandardCharsets.UTF_8);
        Path rightOrder = Files.writeString(tempDir.resolve("right-order.json"), rightJsonOrder, StandardCharsets.UTF_8);

        DiffViewModel viewModel = new DiffViewModel(true);
        viewModel.leftPathProperty().set(left);
        viewModel.rightPathProperty().set(rightOrder);

        viewModel.compare();
        assertThat(viewModel.hunksProperty()).isEmpty();

        viewModel.ignoreJsonKeyOrderProperty().set(false);
        String rightJsonChanged = "{\"outer\":{\"first\":{\"b\":2,\"a\":1},\"second\":{\"y\":false,\"x\":true,\"extra\":1}}}";
        Path rightChanged = Files.writeString(tempDir.resolve("right-changed.json"), rightJsonChanged, StandardCharsets.UTF_8);
        viewModel.rightPathProperty().set(rightChanged);
        viewModel.compare();
        assertThat(viewModel.hunksProperty()).isNotEmpty();
        assertThat(viewModel.hunksProperty())
                .extracting(DiffHunk::getId)
                .allMatch(id -> id.startsWith("json-path-"));
    }

    @Test
    void missingFileRaisesIOExceptionAndKeepsExistingHunks() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.txt"), "a\nb\n", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.txt"), "a\nc\n", StandardCharsets.UTF_8);

        DiffViewModel viewModel = new DiffViewModel();
        viewModel.leftPathProperty().set(left);
        viewModel.rightPathProperty().set(right);
        viewModel.compare();
        assertThat(viewModel.hunksProperty()).isNotEmpty();

        viewModel.rightPathProperty().set(tempDir.resolve("missing.txt"));
        assertThatThrownBy(viewModel::compare).isInstanceOf(IOException.class);
        assertThat(viewModel.hunksProperty()).isNotEmpty();
    }
}
