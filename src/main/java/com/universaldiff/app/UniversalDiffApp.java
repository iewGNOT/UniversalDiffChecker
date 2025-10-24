//this is a comment

package com.universaldiff.app;

import com.universaldiff.core.model.ComparisonSession;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.FormatType;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class UniversalDiffApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(UniversalDiffApp.class);

    private static final int BINARY_BYTES_PER_LINE = 16;
    private static final int TEXT_RENDER_LIMIT = 200_000;
    private static final int BINARY_RENDER_LIMIT = 131_072;

    private final DiffViewModel viewModel = new DiffViewModel();

    private VBox leftColumn;
    private VBox rightColumn;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Universal Difference Checker");

        leftColumn = createDiffColumn("Left");
        rightColumn = createDiffColumn("Right");

        viewModel.hunksProperty().addListener((ListChangeListener<? super DiffHunk>) change -> renderDiffColumns());

        BorderPane root = new BorderPane();
        root.setTop(createToolbar(stage));
        root.setCenter(createContent());

        Scene scene = new Scene(root, 1100, 750);
        scene.getStylesheets().add(getClass().getResource("/com/universaldiff/ui/udc.css").toExternalForm());
        stage.setScene(scene);
        stage.show();

        renderDiffColumns();
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

        ScrollPane leftScroll = wrapColumn(leftColumn);
        ScrollPane rightScroll = wrapColumn(rightColumn);

        VBox leftPane = new VBox(leftScroll);
        VBox rightPane = new VBox(rightScroll);
        VBox.setVgrow(leftScroll, Priority.ALWAYS);
        VBox.setVgrow(rightScroll, Priority.ALWAYS);

        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(0.5);
        return splitPane;
    }

    private ScrollPane wrapColumn(VBox column) {
        ScrollPane scroll = new ScrollPane(column);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("diff-column-scroll");
        return scroll;
    }

    private VBox createDiffColumn(String title) {
        VBox column = new VBox();
        column.getStyleClass().add("diff-column");
        column.getChildren().add(createColumnHeader(title));
        return column;
    }

    private Label createColumnHeader(String title) {
        Label header = new Label(title);
        header.getStyleClass().add("diff-column-header");
        return header;
    }

    private void chooseFile(Stage stage, boolean isLeft) {
        FileChooser chooser = new FileChooser();
        Path initial = isLeft ? viewModel.leftPathProperty().get() : viewModel.rightPathProperty().get();
        if (initial != null && initial.getParent() != null) {
            chooser.setInitialDirectory(initial.getParent().toFile());
        }
        var chosen = chooser.showOpenDialog(stage);
        if (chosen != null) {
            if (isLeft) {
                viewModel.leftPathProperty().set(chosen.toPath());
            } else {
                viewModel.rightPathProperty().set(chosen.toPath());
            }
            renderDiffColumns();
        }
    }

    private void runComparison() {
        try {
            viewModel.compare();
            renderDiffColumns();
        } catch (IOException ex) {
            log.error("Comparison failed while reading files {} and {}",
                    viewModel.leftPathProperty().get(), viewModel.rightPathProperty().get(), ex);
            showError("Comparison failed", ex);
        }
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
            log.error("Merge failed writing to {}", target.toPath(), ex);
            showError("Merge failed", ex);
        }
    }

    private void renderDiffColumns() {
        resetColumn(leftColumn, "Left");
        resetColumn(rightColumn, "Right");

        Optional<ComparisonSession> sessionOpt = viewModel.getCurrentSession();
        if (sessionOpt.isEmpty()) {
            return;
        }

        try {
            ComparisonSession session = sessionOpt.get();
            FormatType formatType = session.getLeft().getFormatType();
            boolean isBinary = isBinaryFormat(formatType);

            if (isBinary) {
                byte[] leftBytes = Files.readAllBytes(session.getLeft().getPath());
                byte[] rightBytes = Files.readAllBytes(session.getRight().getPath());

                populateBinaryColumn(leftColumn, buildBinaryLines(leftBytes, rightBytes), true);
                populateBinaryColumn(rightColumn, buildBinaryLines(rightBytes, leftBytes), false);
            } else {
                String leftText = String.join("\n", session.getLeftContent().getLogicalRecords());
                String rightText = String.join("\n", session.getRightContent().getLogicalRecords());

                boolean leftTruncated = leftText.length() > TEXT_RENDER_LIMIT;
                boolean rightTruncated = rightText.length() > TEXT_RENDER_LIMIT;
                String leftShown = leftTruncated ? leftText.substring(0, TEXT_RENDER_LIMIT) : leftText;
                String rightShown = rightTruncated ? rightText.substring(0, TEXT_RENDER_LIMIT) : rightText;

                populateTextColumn(leftColumn, buildTextLines(leftShown, rightShown), "diff-delta-left", "diff-row-left");
                populateTextColumn(rightColumn, buildTextLines(rightShown, leftShown), "diff-delta-right", "diff-row-right");

                if (leftTruncated) {
                    addTruncationNotice(leftColumn, false, leftText.length(), leftShown.length());
                }
                if (rightTruncated) {
                    addTruncationNotice(rightColumn, false, rightText.length(), rightShown.length());
                }
            }
        } catch (IOException ex) {
            log.error("Failed to render diff columns", ex);
            showError("Render error", ex);
        }
    }

    private boolean isBinaryFormat(FormatType formatType) {
        return switch (formatType) {
            case BIN, HEX -> true;
            default -> false;
        };
    }

    private String readText(Path path, Charset charset) throws IOException {
        return Files.readString(path, charset);
    }

    private void populateTextColumn(VBox column,
                                    List<TextLine> lines,
                                    String diffClass,
                                    String rowClass) {
        for (TextLine line : lines) {
            HBox row = new HBox();
            row.getStyleClass().add("diff-row");
            boolean hasDiff = line.segments().stream().anyMatch(segment -> segment.type() == SegmentType.DIFF);
            if (hasDiff) {
                row.getStyleClass().add(rowClass);
            } else {
                row.getStyleClass().add("diff-row-neutral");
            }

            Label number = new Label(String.valueOf(line.number()));
            number.getStyleClass().add("diff-line-number");

            TextFlow flow = new TextFlow();
            flow.getStyleClass().add("diff-text-flow");

            if (line.segments().isEmpty()) {
                flow.getChildren().add(new Text(""));
            } else {
                for (Segment segment : line.segments()) {
                    if (segment.type() == SegmentType.DIFF) {
                        Label highlight = new Label(segment.text());
                        highlight.getStyleClass().addAll("diff-text-highlight", diffClass);
                        flow.getChildren().add(highlight);
                    } else {
                        Text text = new Text(segment.text());
                        text.getStyleClass().add("diff-text-normal");
                        flow.getChildren().add(text);
                    }
                }
            }

            row.getChildren().addAll(number, flow);
            column.getChildren().add(row);
        }
    }

    private List<TextLine> buildTextLines(String content, String counterpart) {
        List<TextLine> lines = new ArrayList<>();
        if (content == null) {
            lines.add(new TextLine(1, List.of()));
            return lines;
        }

        int len = content.length();
        int otherLen = counterpart == null ? 0 : counterpart.length();

        List<Segment> currentSegments = new ArrayList<>();
        SegmentType currentType = null;
        StringBuilder buffer = new StringBuilder();
        int lineNumber = 1;

        for (int i = 0; i < len; i++) {
            char ch = content.charAt(i);
            if (ch == '\r') {
                continue;
            }
            if (ch == '\n') {
                flushSegment(currentSegments, buffer, currentType);
                lines.add(new TextLine(lineNumber++, List.copyOf(currentSegments)));
                currentSegments.clear();
                currentType = null;
                continue;
            }

            boolean match = i < otherLen && counterpart.charAt(i) == ch;
            SegmentType type = match ? SegmentType.MATCH : SegmentType.DIFF;
            if (type != currentType) {
                flushSegment(currentSegments, buffer, currentType);
                currentType = type;
            }
            buffer.append(ch);
        }

        flushSegment(currentSegments, buffer, currentType);
        lines.add(new TextLine(lineNumber, List.copyOf(currentSegments)));
        return lines;
    }

    private void populateBinaryColumn(VBox column,
                                      List<BinaryLine> lines,
                                      boolean isLeftColumn) {
        for (BinaryLine line : lines) {
            HBox row = new HBox();
            row.getStyleClass().add("diff-row");
            boolean hasDiff = line.cells().stream().anyMatch(BinaryCell::diff);
            if (hasDiff) {
                row.getStyleClass().add(isLeftColumn ? "binary-row-left" : "binary-row-right");
            } else {
                row.getStyleClass().add("binary-row-neutral");
            }

            Label offsetLabel = new Label(String.format("%08X", line.offset()));
            offsetLabel.getStyleClass().add("binary-offset");

            TextFlow hexFlow = new TextFlow();
            hexFlow.getStyleClass().add("binary-hex-flow");

            TextFlow asciiFlow = new TextFlow();
            asciiFlow.getStyleClass().add("binary-ascii-flow");

            for (BinaryCell cell : line.cells()) {
                if (!cell.present()) {
                    Label hexPlaceholder = new Label("  ");
                    hexPlaceholder.getStyleClass().add("binary-hex-byte");
                    hexFlow.getChildren().add(hexPlaceholder);

                    Label asciiPlaceholder = new Label(" ");
                    asciiPlaceholder.getStyleClass().add("binary-ascii-char");
                    asciiFlow.getChildren().add(asciiPlaceholder);
                    continue;
                }

                Label hexLabel = new Label(cell.hex());
                hexLabel.getStyleClass().add("binary-hex-byte");
                if (cell.diff()) {
                    hexLabel.getStyleClass().add(isLeftColumn ? "binary-hex-byte-diff-left" : "binary-hex-byte-diff-right");
                }
                hexFlow.getChildren().add(hexLabel);

                Label asciiLabel = new Label(String.valueOf(cell.ascii()));
                asciiLabel.getStyleClass().add("binary-ascii-char");
                if (cell.diff()) {
                    asciiLabel.getStyleClass().add(isLeftColumn ? "binary-ascii-char-diff-left" : "binary-ascii-char-diff-right");
                }
                asciiFlow.getChildren().add(asciiLabel);
            }

            row.getChildren().addAll(offsetLabel, hexFlow, asciiFlow);
            column.getChildren().add(row);
        }
    }

    private List<BinaryLine> buildBinaryLines(byte[] content, byte[] counterpart) {
        List<BinaryLine> lines = new ArrayList<>();
        int maxLength = Math.max(content.length, counterpart.length);
        for (int base = 0; base < maxLength; base += BINARY_BYTES_PER_LINE) {
            List<BinaryCell> cells = new ArrayList<>();
            for (int i = 0; i < BINARY_BYTES_PER_LINE; i++) {
                int index = base + i;
                if (index < content.length) {
                    int value = content[index] & 0xFF;
                    boolean diff = index >= counterpart.length || content[index] != counterpart[index];
                    String hex = String.format("%02X", value);
                    char ascii = value >= 32 && value <= 126 ? (char) value : '.';
                    cells.add(new BinaryCell(index, hex, ascii, diff, true));
                } else {
                    cells.add(new BinaryCell(index, "  ", ' ', false, false));
                }
            }
            lines.add(new BinaryLine(base, cells));
        }
        if (lines.isEmpty()) {
            lines.add(new BinaryLine(0, List.of()))
            ;
        }
        return lines;
    }


    private void addTruncationNotice(VBox column, boolean binary, long totalLength, long shownLength) {
        HBox row = new HBox();
        row.getStyleClass().addAll("diff-row", "truncated-row");

        Label marker = new Label("\u2026");
        marker.getStyleClass().add("diff-line-number");

        String unit = binary ? "bytes" : "characters";
        String messageText = String.format("Showing first %,d of %,d %s (truncated for performance).",
                shownLength, totalLength, unit);
        Label message = new Label(messageText);
        message.getStyleClass().add("truncated-message");

        row.getChildren().addAll(marker, message);
        column.getChildren().add(row);
    }

    private void flushSegment(List<Segment> segments, StringBuilder buffer, SegmentType currentType) {
        if (currentType != null && buffer.length() > 0) {
            segments.add(new Segment(buffer.toString(), currentType));
            buffer.setLength(0);
        }
    }

    private void resetColumn(VBox column, String title) {
        column.getChildren().clear();
        column.getChildren().add(createColumnHeader(title));
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

    public static void main(String[] args) {
        launch(args);
    }

    private enum SegmentType {
        MATCH,
        DIFF
    }

    private record Segment(String text, SegmentType type) {
    }

    private record TextLine(int number, List<Segment> segments) {
    }

    private record BinaryCell(int index, String hex, char ascii, boolean diff, boolean present) {
    }

    private record BinaryLine(int offset, List<BinaryCell> cells) {
    }
}










