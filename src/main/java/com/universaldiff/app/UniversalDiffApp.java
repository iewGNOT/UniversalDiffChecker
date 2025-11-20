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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;

public class UniversalDiffApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(UniversalDiffApp.class);

    private static final int BINARY_BYTES_PER_LINE = 16;
    private static final int TEXT_RENDER_LIMIT = 200_000;
    private static final int BINARY_RENDER_LIMIT = 131_072;

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

        renderDiffColumns();
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

        IntFunction<Node> baseFactory = LineNumberFactory.get(area);
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
        Task<DiffRenderResult> renderTask = new Task<>() {
            @Override
            protected DiffRenderResult call() throws Exception {
                return buildRenderResult();
            }
        };
        renderTask.setOnSucceeded(e -> {
            DiffRenderResult result = renderTask.getValue();
            Platform.runLater(() -> applyRenderResult(result));
        });
        renderTask.setOnFailed(e -> {
            Throwable failure = renderTask.getException();
            log.error("Failed to render diff columns", failure);
            Platform.runLater(() -> showError("Render error", toException(failure)));
        });
        renderExecutor.submit(renderTask);
    }

    private DiffRenderResult buildRenderResult() throws IOException {
        Optional<ComparisonSession> sessionOpt = viewModel.getCurrentSession();
        if (sessionOpt.isEmpty()) {
            DiffAreaContent placeholder = placeholderContent("Load files and run Compare to see differences.");
            return new DiffRenderResult(placeholder, placeholder);
        }

        ComparisonSession session = sessionOpt.get();
        if (isBinaryFormat(session.getLeft().getFormatType())) {
            BinarySlice leftSlice = readBinarySlice(session.getLeft().getPath());
            BinarySlice rightSlice = readBinarySlice(session.getRight().getPath());

            List<BinaryLine> leftLines = buildBinaryLines(leftSlice.bytes(), rightSlice.bytes());
            List<BinaryLine> rightLines = buildBinaryLines(rightSlice.bytes(), leftSlice.bytes());

            DiffAreaContent leftContent = buildBinaryContent(leftLines, DiffSide.LEFT, leftSlice.truncated(), leftSlice.totalLength(), leftSlice.bytes().length);
            DiffAreaContent rightContent = buildBinaryContent(rightLines, DiffSide.RIGHT, rightSlice.truncated(), rightSlice.totalLength(), rightSlice.bytes().length);
            return new DiffRenderResult(leftContent, rightContent);
        }

        String leftText = String.join("\n", session.getLeftContent().getLogicalRecords());
        String rightText = String.join("\n", session.getRightContent().getLogicalRecords());

        boolean leftTruncated = leftText.length() > TEXT_RENDER_LIMIT;
        boolean rightTruncated = rightText.length() > TEXT_RENDER_LIMIT;
        String leftShown = leftTruncated ? leftText.substring(0, TEXT_RENDER_LIMIT) : leftText;
        String rightShown = rightTruncated ? rightText.substring(0, TEXT_RENDER_LIMIT) : rightText;

        List<TextLine> leftLines = buildTextLines(leftShown, rightShown);
        List<TextLine> rightLines = buildTextLines(rightShown, leftShown);

        DiffAreaContent leftContent = buildTextContent(leftLines, DiffSide.LEFT, leftTruncated, leftText.length(), leftShown.length());
        DiffAreaContent rightContent = buildTextContent(rightLines, DiffSide.RIGHT, rightTruncated, rightText.length(), rightShown.length());
        return new DiffRenderResult(leftContent, rightContent);
    }

    private DiffAreaContent buildTextContent(List<TextLine> lines, DiffSide side, boolean truncated, long totalLength, long shownLength) {
        boolean completelyEmpty = lines.size() == 1 && lines.get(0).segments().isEmpty();
        if (completelyEmpty && !truncated) {
            return placeholderContent("File is empty.");
        }

        StringBuilder builder = new StringBuilder();
        StyleSpansBuilder<String> spans = new StyleSpansBuilder<>();

        for (int i = 0; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            if (!line.segments().isEmpty()) {
                for (Segment segment : line.segments()) {
                    String text = segment.text();
                    if (!text.isEmpty()) {
                        builder.append(text);
                        spans.add(resolveSegmentStyle(side, segment.type()), text.length());
                    }
                }
            }
            if (i < lines.size() - 1) {
                builder.append('\n');
                spans.add(STYLE_TEXT_NORMAL, 1);
            }
        }

        if (builder.length() == 0 && !truncated) {
            return placeholderContent("File contains only blank lines.");
        }

        if (truncated) {
            appendTruncationMessage(builder, spans, false, totalLength, shownLength);
        }

        return new DiffAreaContent(builder.toString(), spans.create());
    }

    private DiffAreaContent buildBinaryContent(List<BinaryLine> lines, DiffSide side, boolean truncated, long totalLength, long shownLength) {
        StringBuilder builder = new StringBuilder();
        StyleSpansBuilder<String> spans = new StyleSpansBuilder<>();

        for (int i = 0; i < lines.size(); i++) {
            BinaryLine line = lines.get(i);
            String offset = String.format("%08X  ", line.offset());
            builder.append(offset);
            spans.add(STYLE_BINARY_OFFSET, offset.length());

            for (BinaryCell cell : line.cells()) {
                String block = (cell.present() ? cell.hex() : "  ") + " ";
                builder.append(block);
                spans.add(resolveBinaryStyle(side, cell.diff() && cell.present()), block.length());
            }

            builder.append("|");
            spans.add(STYLE_BINARY_OFFSET, 1);
            for (BinaryCell cell : line.cells()) {
                String ascii = cell.present() ? String.valueOf(cell.ascii()) : " ";
                builder.append(ascii);
                spans.add(resolveBinaryStyle(side, cell.diff() && cell.present()), ascii.length());
            }
            builder.append("|");
            spans.add(STYLE_BINARY_OFFSET, 1);

            if (i < lines.size() - 1) {
                builder.append('\n');
                spans.add(STYLE_TEXT_NORMAL, 1);
            }
        }

        if (builder.length() == 0 && !truncated) {
            return placeholderContent("Binary data is empty.");
        }

        if (truncated) {
            appendTruncationMessage(builder, spans, true, totalLength, shownLength);
        }

        return new DiffAreaContent(builder.toString(), spans.create());
    }

    private void appendTruncationMessage(StringBuilder builder, StyleSpansBuilder<String> spans,
                                         boolean binary, long total, long shown) {
        if (builder.length() > 0) {
            builder.append('\n');
            spans.add(STYLE_TEXT_NORMAL, 1);
        }
        String unit = binary ? "bytes" : "characters";
        String message = String.format("\u2026 Showing first %,d of %,d %s for performance.", shown, total, unit);
        builder.append(message);
        spans.add(STYLE_TEXT_INFO, message.length());
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

    private void applyRenderResult(DiffRenderResult result) {
        applyContent(leftTextArea, result.left());
        applyContent(rightTextArea, result.right());
    }

    private void applyContent(InlineCssTextArea area, DiffAreaContent content) {
        area.replaceText(content.text());
        area.setStyleSpans(0, content.spans());
        area.moveTo(0);
    }

    private DiffAreaContent placeholderContent(String message) {
        StyleSpansBuilder<String> spans = new StyleSpansBuilder<>();
        spans.add(STYLE_TEXT_MUTED, message.length());
        return new DiffAreaContent(message, spans.create());
    }

    private boolean isBinaryFormat(FormatType formatType) {
        return switch (formatType) {
            case BIN, HEX -> true;
            default -> false;
        };
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
            lines.add(new BinaryLine(0, List.of()));
        }
        return lines;
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

    private Exception toException(Throwable throwable) {
        return throwable instanceof Exception ? (Exception) throwable : new Exception(throwable);
    }

    @Override
    public void stop() {
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

    private record DiffAreaContent(String text, StyleSpans<String> spans) {
    }

    private record DiffRenderResult(DiffAreaContent left, DiffAreaContent right) {
    }
}
