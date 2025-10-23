package com.universaldiff.ui.viewmodel;

import com.universaldiff.core.ComparisonService;
import com.universaldiff.core.model.ComparisonOptions;
import com.universaldiff.core.model.ComparisonSession;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class DiffViewModel {

    private ComparisonService comparisonService;
    private final ObjectProperty<Path> leftPath = new SimpleObjectProperty<>();
    private final ObjectProperty<Path> rightPath = new SimpleObjectProperty<>();
    private final ObservableList<DiffHunk> hunks = FXCollections.observableArrayList();
    private final BooleanProperty ignoreJsonKeyOrder = new SimpleBooleanProperty(true);
    private ComparisonSession currentSession;

    public DiffViewModel() {
        this(true);
    }

    public DiffViewModel(boolean ignoreJsonKeyOrder) {
        rebuildComparisonService(ignoreJsonKeyOrder);
        this.ignoreJsonKeyOrder.set(ignoreJsonKeyOrder);
        this.ignoreJsonKeyOrder.addListener((obs, oldVal, newVal) -> rebuildComparisonService(newVal));
    }

    private void rebuildComparisonService(boolean ignoreOrder) {
        this.comparisonService = ComparisonService.createDefault(ignoreOrder);
    }

    public ObjectProperty<Path> leftPathProperty() {
        return leftPath;
    }

    public ObjectProperty<Path> rightPathProperty() {
        return rightPath;
    }

    public BooleanProperty ignoreJsonKeyOrderProperty() {
        return ignoreJsonKeyOrder;
    }

    public ObservableList<DiffHunk> hunksProperty() {
        return hunks;
    }

    public void compare() throws IOException {
        Path left = leftPath.get();
        Path right = rightPath.get();
        if (left == null || right == null) {
            throw new IOException("Both files must be selected");
        }
        ensureExists(left);
        ensureExists(right);
        currentSession = comparisonService.compare(left, right, ComparisonOptions.builder().build());
        DiffResult diffResult = currentSession.getDiffResult();
        hunks.setAll(diffResult.getHunks());
    }

    public List<String> readFilePreview(Path path) throws IOException {
        return Files.readAllLines(path);
    }

    public MergeResult merge(List<MergeDecision> decisions, Path outputPath) throws IOException {
        if (currentSession == null) {
            throw new IOException("No active comparison session");
        }
        return currentSession.merge(decisions, outputPath);
    }

    public Optional<ComparisonSession> getCurrentSession() {
        return Optional.ofNullable(currentSession);
    }

    private void ensureExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }
    }
}

