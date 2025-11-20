package com.universaldiff.app;

import com.universaldiff.core.model.ComparisonSession;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.ui.viewmodel.DiffViewModel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class UniversalDiffApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(UniversalDiffApp.class);

    private static final int BINARY_BYTES_PER_LINE = 16;
    private static final int TEXT_RENDER_LIMIT = 200_000;
    private static final int BINARY_RENDER_LIMIT = 131_072;
    private static final int TEXT_STREAM_LINES = 400;
    private static final int BINARY_STREAM_LINES = 200;

    private static final Font TITLE_FONT = Font.font("Segoe UI Semibold", 20);
    private static final Font SECTION_FONT = Font.font("Segoe UI Semibold", 15);
    private static final Font CONTROL_FONT = Font.font("Segoe UI", 13);
    private static final Font MONO_LABEL_FONT = Font.font("Consolas", FontWeight.SEMI_BOLD, 12);

    private static final Color TEXT_COLOR = Color.rgb(26, 28, 34);
    private static final Color MUTED_TEXT_COLOR = Color.rgb(109, 113, 122);
    private static final Color ACCENT_COLOR = Color.web("#2563EB");
    private static final Background ROOT_BACKGROUND = new Background(new BackgroundFill(Color.rgb(247, 248, 251), CornerRadii.EMPTY, Insets.EMPTY));
    private static final Background CARD_BACKGROUND = new Background(new BackgroundFill(Color.WHITE, new CornerRadii(14), Insets.EMPTY));
    private static final Border CARD_BORDER = new Border(new BorderStroke(Color.rgb(227, 230, 235), BorderStrokeStyle.SOLID, new CornerRadii(14), new BorderWidths(1)));
    private static final Background LINE_NUMBER_BACKGROUND = new Background(new BackgroundFill(Color.rgb(239, 241, 245), new CornerRadii(6), Insets.EMPTY));
    private static final Border LINE_NUMBER_BORDER = new Border(new BorderStroke(Color.rgb(225, 228, 233), BorderStrokeStyle.SOLID, new CornerRadii(6), new BorderWidths(1)));

    private static final String STYLE_TEXT_NORMAL = "-fx-fill: #1A1C22;";
    private static final String STYLE_TEXT_MUTED = "-fx-fill: #6D717A;";
    private static final String STYLE_TEXT_INFO = "-fx-fill: #475467; -fx-font-style: italic;";
    private static final String STYLE_DIFF_LEFT = "-fx-fill: #1A1C22; -rtfx-background-color: rgba(248,113,113,0.25); -fx-font-weight: 600;";
    private static final String STYLE_DIFF_RIGHT = "-fx-fill: #1A1C22; -rtfx-background-color: rgba(74,222,128,0.25); -fx-font-weight: 600;";
    private static final String STYLE_BINARY_OFFSET = "-fx-fill: #6D717A; -fx-font-weight: 600;";

    private final DiffViewModel viewModel = new DiffViewModel();

    private InlineCssTextArea leftTextArea;
    private InlineCssTextArea rightTextArea;

    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "diff-renderer");
        thread.setDaemon(true);
        return thread;
    });
    private DiffStreamTask currentRenderTask;
    private Future<?> currentRenderFuture;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Universal Difference Checker");

        leftTextArea = createDiffArea();
        rightTextArea = createDiffArea();

        BorderPane root = new BorderPane();
        root.setBackground(ROOT_BACKGROUND);

        VBox topSection = new VBox();
        topSection.setSpacing(12);
        topSection.setPadding(new Insets(24, 24, 16, 24));
        topSection.getChildren().addAll(buildTitleBar(), createCommandBar(stage));
        root.setTop(topSection);
        root.setCenter(createDiffContent());

        Scene scene = new Scene(root, 1200, 780);
        stage.setScene(scene);
        stage.show();

        showMessage(leftTextArea, "Load files and run Compare to see differences.", STYLE_TEXT_MUTED);
        showMessage(rightTextArea, "Load files and run Compare to see differences.", STYLE_TEXT_MUTED);
    }

    private Node buildTitleBar() {
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setSpacing(12);

        Label title = new Label("Universal Difference Checker");
        title.setFont(TITLE_FONT);
        title.setTextFill(TEXT_COLOR);

        Label subtitle = new Label("Compare and merge complex files with confidence");
        subtitle.setFont(Font.font("Segoe UI", 13));
        subtitle.setTextFill(MUTED_TEXT_COLOR);

        VBox textStack = new VBox(title, subtitle);
        textStack.setSpacing(2);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label status = new Label("Ready");
        status.setFont(CONTROL_FONT);
        status.setTextFill(Color.rgb(34, 197, 94));

        titleBar.getChildren().addAll(textStack, spacer, status);
        return titleBar;
    }

    private HBox createCommandBar(Stage stage) {
        Button openLeft = createSecondaryButton("Open Left");
        openLeft.setOnAction(e -> chooseFile(stage, true));
        Button openRight = createSecondaryButton("Open Right");
        openRight.setOnAction(e -> chooseFile(stage, false));
        Button compareButton = createPrimaryButton("Compare");
        compareButton.setOnAction(e -> runComparison());

        HBox fileControls = new HBox(openLeft, openRight, compareButton);
        fileControls.setSpacing(8);

        CheckBox ignoreJsonCheck = new CheckBox("Ignore JSON key order");
        ignoreJsonCheck.setFont(CONTROL_FONT);
        ignoreJsonCheck.setTextFill(TEXT_COLOR);
        ignoreJsonCheck.selectedProperty().bindBidirectional(viewModel.ignoreJsonKeyOrderProperty());

        ComboBox<MergeChoice> mergeStrategy = new ComboBox<>();
        mergeStrategy.getItems().addAll(MergeChoice.TAKE_LEFT, MergeChoice.TAKE_RIGHT, MergeChoice.MANUAL);
        mergeStrategy.getSelectionModel().select(MergeChoice.TAKE_RIGHT);
        mergeStrategy.setPrefWidth(180);
        mergeStrategy.setPrefHeight(36);
        mergeStrategy.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(MergeChoice choice, boolean empty) {
                super.updateItem(choice, empty);
                if (empty || choice == null) {
                    setText(null);
                } else {
                    setText(choice.name().replace('_', ' '));
                }
                setFont(CONTROL_FONT);
            }
        });
        mergeStrategy.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(MergeChoice choice, boolean empty) {
                super.updateItem(choice, empty);
                if (empty || choice == null) {
                    setText(null);
                } else {
                    setText(choice.name().replace('_', ' '));
                }
                setFont(CONTROL_FONT);
            }
        });

        Label mergeLabel = new Label("Merge strategy");
        mergeLabel.setFont(CONTROL_FONT);
        mergeLabel.setTextFill(MUTED_TEXT_COLOR);

        Button mergeButton = createPrimaryButton("Merge");
        mergeButton.setOnAction(e -> runMerge(stage, mergeStrategy.getValue()));

        HBox mergeBox = new HBox(mergeLabel, mergeStrategy, mergeButton);
        mergeBox.setSpacing(8);
        mergeBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setSpacing(16);
        bar.setPadding(new Insets(16));
        bar.setBackground(CARD_BACKGROUND);
        bar.setBorder(CARD_BORDER);
        bar.getChildren().addAll(fileControls, new Separator(Orientation.VERTICAL), mergeBox, spacer, ignoreJsonCheck);
        return bar;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.setFont(CONTROL_FONT);
        button.setTextFill(Color.WHITE);
        button.setBackground(new Background(new BackgroundFill(ACCENT_COLOR, new CornerRadii(8), Insets.EMPTY)));
        button.setBorder(Border.EMPTY);
        button.setMinHeight(36);
        button.setPadding(new Insets(6, 18, 6, 18));
        button.setFocusTraversable(false);
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.setFont(CONTROL_FONT);
        button.setTextFill(ACCENT_COLOR);
        button.setBackground(new Background(new BackgroundFill(Color.WHITE, new CornerRadii(8), Insets.EMPTY)));
        button.setBorder(new Border(new BorderStroke(ACCENT_COLOR, BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(1))));
        button.setMinHeight(36);
        button.setPadding(new Insets(6, 18, 6, 18));
        button.setFocusTraversable(false);
        return button;
    }

    private Node createDiffContent() {
        VirtualizedScrollPane<InlineCssTextArea> leftPane = new VirtualizedScrollPane<>(leftTextArea);
        VirtualizedScrollPane<InlineCssTextArea> rightPane = new VirtualizedScrollPane<>(rightTextArea);

        VBox leftCard = buildDiffCard("Left source", leftPane);
        VBox rightCard = buildDiffCard("Right source", rightPane);

        HBox content = new HBox(leftCard, rightCard);
        content.setSpacing(16);
        content.setPadding(new Insets(0, 24, 24, 24));
        HBox.setHgrow(leftCard, Priority.ALWAYS);
        HBox.setHgrow(rightCard, Priority.ALWAYS);
        return content;
    }

    private VBox buildDiffCard(String title, Node contentNode) {
        Label header = new Label(title);
        header.setFont(SECTION_FONT);
        header.setTextFill(TEXT_COLOR);

        VBox card = new VBox(header, contentNode);
        card.setSpacing(12);
        card.setBackground(CARD_BACKGROUND);
        card.setBorder(CARD_BORDER);
        card.setPadding(new Insets(16, 20, 20, 20));
        VBox.setVgrow(contentNode, Priority.ALWAYS);
        return card;
    }

    private InlineCssTextArea createDiffArea() {
        InlineCssTextArea area = new InlineCssTextArea();
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");
        area.setFocusTraversable(false);

        LineNumberFactory.get(area);
        var baseFactory = LineNumberFactory.get(area);
        area.setParagraphGraphicFactory(line -> {
            Node node = baseFactory.apply(line);
            if (node instanceof Label label) {
                label.setFont(MONO_LABEL_FONT);
                label.setTextFill(MUTED_TEXT_COLOR);
                label.setBackground(LINE_NUMBER_BACKGROUND);
                label.setBorder(LINE_NUMBER_BORDER);
                label.setPadding(new Insets(0, 12, 0, 0));
            }
            return node;
        });
        return area;
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
        Task<Void> comparisonTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                viewModel.compare();
                return null;
            }
        };
        comparisonTask.setOnSucceeded(e -> Platform.runLater(this::renderDiffColumns));
        comparisonTask.setOnFailed(e -> {
            Throwable failure = comparisonTask.getException();
            log.error("Comparison failed while reading files {} and {}", viewModel.leftPathProperty().get(), viewModel.rightPathProperty().get(), failure);
            Platform.runLater(() -> showError("Comparison failed", toException(failure)));
        });
        Thread thread = new Thread(comparisonTask, "diff-compare");
        thread.setDaemon(true);
        thread.start();
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
        cancelCurrentRender();
        Optional<ComparisonSession> sessionOpt = viewModel.getCurrentSession();
        if (sessionOpt.isEmpty()) {
            showMessage(leftTextArea, "Load files and run Compare to see differences.", STYLE_TEXT_MUTED);
            showMessage(rightTextArea, "Load files and run Compare to see differences.", STYLE_TEXT_MUTED);
            return;
        }

        showMessage(leftTextArea, "Preparing diff...", STYLE_TEXT_INFO);
        showMessage(rightTextArea, "Preparing diff...", STYLE_TEXT_INFO);

        currentRenderTask = new DiffStreamTask(sessionOpt.get());
        currentRenderTask.setOnFailed(e -> {
            Throwable failure = currentRenderTask.getException();
            if (!(failure instanceof CancellationException)) {
                log.error("Failed to render diff columns", failure);
                Platform.runLater(() -> showError("Render error", toException(failure)));
            }
        });
        currentRenderFuture = renderExecutor.submit(currentRenderTask);
    }

    private void cancelCurrentRender() {
        if (currentRenderTask != null) {
            currentRenderTask.cancel(true);
            currentRenderTask = null;
        }
        if (currentRenderFuture != null) {
            currentRenderFuture.cancel(true);
            currentRenderFuture = null;
        }
    }

    private void showMessage(InlineCssTextArea area, String message, String style) {
        Platform.runLater(() -> {
            area.replaceText(message);
            if (!message.isEmpty()) {
                area.setStyle(0, message.length(), style);
            }
        });
    }

    private void appendChunk(InlineCssTextArea area, String text, StyleSpans<String> spans) {
        Platform.runLater(() -> {
            int start = area.getLength();
            area.appendText(text);
            area.setStyleSpans(start, spans);
        });
    }

    private class DiffStreamTask extends Task<Void> {

        private final ComparisonSession session;

        private DiffStreamTask(ComparisonSession session) {
            this.session = session;
        }

        @Override
        protected Void call() throws Exception {
            if (isBinaryFormat(session.getLeft().getFormatType())) {
                streamBinary();
            } else {
                streamText();
            }
            return null;
        }

        private void streamText() throws IOException {
            clearForStreaming();
            String leftText = String.join("\n", session.getLeftContent().getLogicalRecords());
            String rightText = String.join("\n", session.getRightContent().getLogicalRecords());

            boolean leftTruncated = leftText.length() > TEXT_RENDER_LIMIT;
            boolean rightTruncated = rightText.length() > TEXT_RENDER_LIMIT;
            String leftShown = leftTruncated ? leftText.substring(0, TEXT_RENDER_LIMIT) : leftText;
            String rightShown = rightTruncated ? rightText.substring(0, TEXT_RENDER_LIMIT) : rightText;

            TextChunkAppender leftAppender = new TextChunkAppender(leftTextArea, DiffSide.LEFT, TEXT_STREAM_LINES);
            produceTextLines(leftShown, rightShown, line -> {
                checkCancelled();
                leftAppender.appendLine(line);
            });
            leftAppender.finish(leftTruncated, leftText.length(), leftShown.length());
            if (!leftAppender.hasProducedContent() && !leftTruncated) {
                showMessage(leftTextArea, "File is empty.", STYLE_TEXT_MUTED);
            }

            TextChunkAppender rightAppender = new TextChunkAppender(rightTextArea, DiffSide.RIGHT, TEXT_STREAM_LINES);
            produceTextLines(rightShown, leftShown, line -> {
                checkCancelled();
                rightAppender.appendLine(line);
            });
            rightAppender.finish(rightTruncated, rightText.length(), rightShown.length());
            if (!rightAppender.hasProducedContent() && !rightTruncated) {
                showMessage(rightTextArea, "File is empty.", STYLE_TEXT_MUTED);
            }
        }

        private void streamBinary() throws IOException {
            clearForStreaming();
            BinarySlice leftSlice = readBinarySlice(session.getLeft().getPath());
            BinarySlice rightSlice = readBinarySlice(session.getRight().getPath());

            BinaryChunkAppender leftAppender = new BinaryChunkAppender(leftTextArea, DiffSide.LEFT, BINARY_STREAM_LINES);
            streamBinaryLines(leftSlice.bytes(), rightSlice.bytes(), leftAppender);
            leftAppender.finish(leftSlice.truncated(), leftSlice.totalLength(), leftSlice.bytes().length);
            if (!leftAppender.hasProducedContent() && !leftSlice.truncated()) {
                showMessage(leftTextArea, "Binary data is empty.", STYLE_TEXT_MUTED);
            }

            BinaryChunkAppender rightAppender = new BinaryChunkAppender(rightTextArea, DiffSide.RIGHT, BINARY_STREAM_LINES);
            streamBinaryLines(rightSlice.bytes(), leftSlice.bytes(), rightAppender);
            rightAppender.finish(rightSlice.truncated(), rightSlice.totalLength(), rightSlice.bytes().length);
            if (!rightAppender.hasProducedContent() && !rightSlice.truncated()) {
                showMessage(rightTextArea, "Binary data is empty.", STYLE_TEXT_MUTED);
            }
        }

        private void clearForStreaming() {
            Platform.runLater(() -> {
                leftTextArea.clear();
                rightTextArea.clear();
            });
        }

        private void checkCancelled() {
            if (isCancelled()) {
                throw new CancellationException("render cancelled");
            }
        }
    }

    private void produceTextLines(String content, String counterpart, Consumer<TextLine> consumer) {
        if (content == null) {
            consumer.accept(new TextLine(1, List.of()));
            return;
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
                consumer.accept(new TextLine(lineNumber++, List.copyOf(currentSegments)));
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
        consumer.accept(new TextLine(lineNumber, List.copyOf(currentSegments)));
    }

    private void streamBinaryLines(byte[] content, byte[] counterpart, BinaryChunkAppender appender) {
        int maxLength = Math.max(content.length, counterpart.length);
        for (int base = 0; base < maxLength; base += BINARY_BYTES_PER_LINE) {
            List<BinaryCell> cells = new ArrayList<>(BINARY_BYTES_PER_LINE);
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
            appender.appendLine(new BinaryLine(base, cells));
        }
    }

    private boolean isBinaryFormat(FormatType formatType) {
        return switch (formatType) {
            case BIN, HEX -> true;
            default -> false;
        };
    }

    private BinarySlice readBinarySlice(Path path) throws IOException {
        long totalLength = Files.exists(path) ? Files.size(path) : 0;
        if (totalLength <= BINARY_RENDER_LIMIT) {
            return new BinarySlice(Files.readAllBytes(path), totalLength);
        }

        byte[] buffer = new byte[BINARY_RENDER_LIMIT];
        int offset = 0;
        try (InputStream in = Files.newInputStream(path)) {
            while (offset < buffer.length) {
                int read = in.read(buffer, offset, buffer.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }
        if (offset < buffer.length) {
            byte[] exact = new byte[offset];
            System.arraycopy(buffer, 0, exact, 0, offset);
            buffer = exact;
        }
        return new BinarySlice(buffer, totalLength);
    }

    private void flushSegment(List<Segment> segments, StringBuilder buffer, SegmentType currentType) {
        if (currentType != null && buffer.length() > 0) {
            segments.add(new Segment(buffer.toString(), currentType));
            buffer.setLength(0);
        }
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

    private Exception toException(Throwable throwable) {
        return throwable instanceof Exception ? (Exception) throwable : new Exception(throwable);
    }

    @Override
    public void stop() {
        cancelCurrentRender();
        renderExecutor.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private enum SegmentType {
        MATCH,
        DIFF
    }

    private enum DiffSide {
        LEFT,
        RIGHT
    }

    private record Segment(String text, SegmentType type) {
    }

    private record TextLine(int number, List<Segment> segments) {
    }

    private record BinaryCell(int index, String hex, char ascii, boolean diff, boolean present) {
    }

    private record BinaryLine(int offset, List<BinaryCell> cells) {
    }

    private record BinarySlice(byte[] bytes, long totalLength) {
        boolean truncated() {
            return totalLength > bytes.length;
        }
    }

    private final class TextChunkAppender {
        private final InlineCssTextArea area;
        private final DiffSide side;
        private final int linesPerChunk;

        private StringBuilder textBuilder = new StringBuilder();
        private StyleSpansBuilder<String> spansBuilder = new StyleSpansBuilder<>();
        private int linesInChunk = 0;
        private long totalLines = 0;
        private boolean producedContent = false;

        private TextChunkAppender(InlineCssTextArea area, DiffSide side, int linesPerChunk) {
            this.area = area;
            this.side = side;
            this.linesPerChunk = linesPerChunk;
        }

        void appendLine(TextLine line) {
            appendLineBreakIfNeeded();
            if (line.segments().isEmpty()) {
                producedContent = true;
                totalLines++;
                linesInChunk++;
                if (linesInChunk >= linesPerChunk) {
                    flush();
                }
                return;
            }
            for (Segment segment : line.segments()) {
                String text = segment.text();
                if (!text.isEmpty()) {
                    textBuilder.append(text);
                    spansBuilder.add(resolveSegmentStyle(side, segment.type()), text.length());
                }
            }
            producedContent = true;
            totalLines++;
            linesInChunk++;
            if (linesInChunk >= linesPerChunk) {
                flush();
            }
        }

        private void appendLineBreakIfNeeded() {
            if (textBuilder.length() > 0) {
                textBuilder.append('\n');
                spansBuilder.add(STYLE_TEXT_NORMAL, 1);
            } else if (totalLines > 0) {
                textBuilder.append('\n');
                spansBuilder.add(STYLE_TEXT_NORMAL, 1);
            }
        }

        void finish(boolean truncated, long totalLength, long shownLength) {
            flush();
            if (truncated) {
                appendInfoChunk(String.format("... Showing first %,d of %,d characters for performance.", shownLength, totalLength));
            }
        }

        boolean hasProducedContent() {
            return producedContent;
        }

        private void appendInfoChunk(String message) {
            StyleSpansBuilder<String> infoSpans = new StyleSpansBuilder<>();
            infoSpans.add(STYLE_TEXT_INFO, message.length());
            appendChunk(area, message, infoSpans.create());
        }

        private void flush() {
            if (textBuilder.length() == 0) {
                linesInChunk = 0;
                return;
            }
            StyleSpans<String> spans = spansBuilder.create();
            appendChunk(area, textBuilder.toString(), spans);
            textBuilder = new StringBuilder();
            spansBuilder = new StyleSpansBuilder<>();
            linesInChunk = 0;
        }
    }

    private final class BinaryChunkAppender {
        private final InlineCssTextArea area;
        private final DiffSide side;
        private final int linesPerChunk;

        private StringBuilder textBuilder = new StringBuilder();
        private StyleSpansBuilder<String> spansBuilder = new StyleSpansBuilder<>();
        private int linesInChunk = 0;
        private boolean producedContent = false;

        private BinaryChunkAppender(InlineCssTextArea area, DiffSide side, int linesPerChunk) {
            this.area = area;
            this.side = side;
            this.linesPerChunk = linesPerChunk;
        }

        void appendLine(BinaryLine line) {
            if (textBuilder.length() > 0) {
                textBuilder.append('\n');
                spansBuilder.add(STYLE_TEXT_NORMAL, 1);
            }

            String offset = String.format("%08X  ", line.offset());
            textBuilder.append(offset);
            spansBuilder.add(STYLE_BINARY_OFFSET, offset.length());

            for (BinaryCell cell : line.cells()) {
                String block = (cell.present() ? cell.hex() : "  ") + " ";
                textBuilder.append(block);
                spansBuilder.add(resolveBinaryStyle(side, cell.diff() && cell.present()), block.length());
            }

            textBuilder.append("|");
            spansBuilder.add(STYLE_BINARY_OFFSET, 1);
            for (BinaryCell cell : line.cells()) {
                String ascii = cell.present() ? String.valueOf(cell.ascii()) : " ";
                textBuilder.append(ascii);
                spansBuilder.add(resolveBinaryStyle(side, cell.diff() && cell.present()), ascii.length());
            }
            textBuilder.append("|");
            spansBuilder.add(STYLE_BINARY_OFFSET, 1);

            linesInChunk++;
            producedContent = true;
            if (linesInChunk >= linesPerChunk) {
                flush();
            }
        }

        void finish(boolean truncated, long totalLength, long shownLength) {
            flush();
            if (truncated) {
                appendInfoChunk(String.format("... Showing first %,d of %,d bytes for performance.", shownLength, totalLength));
            }
        }

        boolean hasProducedContent() {
            return producedContent;
        }

        private void appendInfoChunk(String message) {
            StyleSpansBuilder<String> builder = new StyleSpansBuilder<>();
            builder.add(STYLE_TEXT_INFO, message.length());
            appendChunk(area, message, builder.create());
        }

        private void flush() {
            if (textBuilder.length() == 0) {
                linesInChunk = 0;
                return;
            }
            appendChunk(area, textBuilder.toString(), spansBuilder.create());
            textBuilder = new StringBuilder();
            spansBuilder = new StyleSpansBuilder<>();
            linesInChunk = 0;
        }
    }

    private String resolveSegmentStyle(DiffSide side, SegmentType type) {
        if (type == SegmentType.DIFF) {
            return side == DiffSide.LEFT ? STYLE_DIFF_LEFT : STYLE_DIFF_RIGHT;
        }
        return STYLE_TEXT_NORMAL;
    }

    private String resolveBinaryStyle(DiffSide side, boolean diff) {
        if (diff) {
            return side == DiffSide.LEFT ? STYLE_DIFF_LEFT : STYLE_DIFF_RIGHT;
        }
        return STYLE_TEXT_NORMAL;
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

}
