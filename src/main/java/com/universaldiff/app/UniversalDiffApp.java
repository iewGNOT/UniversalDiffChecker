package com.universaldiff.app;

import com.universaldiff.core.model.DiffFragment;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.ui.viewmodel.DiffViewModel;
import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class UniversalDiffApp extends Application {

    private final DiffViewModel viewModel = new DiffViewModel();

    private final TextArea leftPreview = createReadOnlyArea();
    private final TextArea rightPreview = createReadOnlyArea();
    private final TextArea diffDetail = createReadOnlyArea();

    @Override
    public void start(Stage stage) {
        stage.setTitle("Universal Difference Checker");
        BorderPane root = new BorderPane();
        root.setTop(createToolbar(stage));
        root.setCenter(createContent());
        stage.setScene(new Scene(root, 1200, 800));
        stage.show();
    }

    private ToolBar createToolbar(Stage stage) {
        Button openLeft = new Button("Open Left");
        openLeft.setOnAction(e -> chooseFile(stage, true));
        Button openRight = new Button("Open Right");
        openRight.setOnAction(e -> chooseFile(stage, false));

        Button compareButton = new Button("Compare");
        compareButton.setOnAction(e -> runComparison());

        CheckBox ignoreJsonCheck = new CheckBox("Ignore JSON key order");
        ignoreJsonCheck.selectedProperty().bindBidirectional(viewModel.ignoreJsonKeyOrderProperty());

        ComboBox<MergeChoice> mergeStrategy = new ComboBox<>();
        mergeStrategy.getItems().addAll(MergeChoice.TAKE_LEFT, MergeChoice.TAKE_RIGHT, MergeChoice.MANUAL);
        mergeStrategy.getSelectionModel().select(MergeChoice.TAKE_RIGHT);

        Button mergeButton = new Button("Merge");
        mergeButton.setOnAction(e -> runMerge(stage, mergeStrategy.getValue()));

        return new ToolBar(openLeft, openRight, compareButton, new Label("Merge strategy:"), mergeStrategy, mergeButton, ignoreJsonCheck);
    }

    private SplitPane createContent() {
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);

        VBox leftPane = new VBox(new Label("Left"), leftPreview);
        VBox rightPane = new VBox(new Label("Right"), rightPreview);
        VBox diffPane = new VBox(new Label("Diffs"), createDiffListView(), new Label("Detail"), diffDetail);

        VBox.setVgrow(leftPreview, Priority.ALWAYS);
        VBox.setVgrow(rightPreview, Priority.ALWAYS);
        VBox.setVgrow(diffDetail, Priority.ALWAYS);

        splitPane.getItems().addAll(leftPane, rightPane, diffPane);
        splitPane.setDividerPositions(0.33, 0.66);
        return splitPane;
    }

    private ListView<DiffHunk> createDiffListView() {
        ListView<DiffHunk> listView = new ListView<>(viewModel.hunksProperty());
        listView.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(DiffHunk item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getType() + ": " + item.getSummary());
                }
            }
        });
        listView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<? super DiffHunk>) change -> showHunkDetail(listView.getSelectionModel().getSelectedItem()));
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> showHunkDetail(newVal));
        return listView;
    }

    private void chooseFile(Stage stage, boolean isLeft) {
        FileChooser chooser = new FileChooser();
        Path initial = isLeft ? viewModel.leftPathProperty().get() : viewModel.rightPathProperty().get();
        if (initial != null) {
            chooser.setInitialDirectory(initial.getParent().toFile());
        }
        Path selected = null;
        var file = chooser.showOpenDialog(stage);
        if (file != null) {
            selected = file.toPath();
        }
        if (selected != null) {
            if (isLeft) {
                viewModel.leftPathProperty().set(selected);
                updatePreview(selected, leftPreview);
            } else {
                viewModel.rightPathProperty().set(selected);
                updatePreview(selected, rightPreview);
            }
        }
    }

    private void updatePreview(Path path, TextArea target) {
        try {
            List<String> lines = viewModel.readFilePreview(path);
            String preview = String.join(System.lineSeparator(), lines);
            if (preview.length() > 10000) {
                preview = preview.substring(0, 10000) + "\n... (truncated)";
            }
            target.setText(preview);
        } catch (IOException ex) {
            showError("Failed to read file", ex);
        }
    }

    private void runComparison() {
        try {
            viewModel.compare();
        } catch (IOException ex) {
            showError("Comparison failed", ex);
        }
    }

    private void showHunkDetail(DiffHunk hunk) {
        if (hunk == null) {
            diffDetail.clear();
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (DiffFragment fragment : hunk.getFragments()) {
            builder.append(fragment.getSide()).append(" -> ").append(fragment.getContent()).append(System.lineSeparator());
        }
        diffDetail.setText(builder.toString());
    }

    private void runMerge(Stage stage, MergeChoice choice) {
        if (viewModel.getCurrentSession().isEmpty()) {
            showError("Merge error", new IllegalStateException("Run a comparison first."));
            return;
        }
        if (choice == MergeChoice.MANUAL) {
            showError("Merge error", new UnsupportedOperationException("Manual merge decisions via UI not implemented yet."));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("merged-output" + determineExtension());
        var target = chooser.showSaveDialog(stage);
        if (target == null) {
            return;
        }
        List<MergeDecision> decisions = new ArrayList<>();
        for (DiffHunk hunk : viewModel.hunksProperty()) {
            decisions.add(new MergeDecision(hunk.getId(), choice, null));
        }
        try {
            viewModel.merge(decisions, target.toPath());
            showInfo("Merge completed", "Merged output saved to " + target.toPath());
        } catch (IOException ex) {
            showError("Merge failed", ex);
        }
    }

    private String determineExtension() {
        return viewModel.getCurrentSession()
                .map(session -> switch (session.getLeft().getFormatType()) {
                    case TXT -> ".txt";
                    case CSV -> ".csv";
                    case JSON -> ".json";
                    case XML -> ".xml";
                    case BIN, HEX -> ".bin";
                    default -> "";
                })
                .orElse(".txt");
    }

    private void showError(String title, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(ex.getMessage());
        alert.setContentText(ex.toString());
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private TextArea createReadOnlyArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(false);
        return area;
    }

    public static void main(String[] args) {
        launch(args);
    }
}




