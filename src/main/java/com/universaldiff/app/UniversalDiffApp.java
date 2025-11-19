package com.universaldiff.app;

import com.universaldiff.core.model.ComparisonSession;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.ui.viewmodel.DiffViewModel;
import javafx.application.Application;
import javafx.collections.ListChangeListener;
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
import javafx.scene.control.ListView;
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
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UniversalDiffApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(UniversalDiffApp.class);

    private static final int BINARY_BYTES_PER_LINE = 16;
    private static final int TEXT_RENDER_LIMIT = 200_000;
    private static final int BINARY_RENDER_LIMIT = 131_072;

    private static final Font TITLE_FONT = Font.font("Segoe UI Semibold", 20);
    private static final Font SECTION_FONT = Font.font("Segoe UI Semibold", 15);
    private static final Font CONTROL_FONT = Font.font("Segoe UI", 13);
    private static final Font MONO_FONT = Font.font("Consolas", 13);
    private static final Font MONO_LABEL_FONT = Font.font("Consolas", FontWeight.SEMI_BOLD, 12);

    private static final Color TEXT_COLOR = Color.rgb(26, 28, 34);
    private static final Color MUTED_TEXT_COLOR = Color.rgb(109, 113, 122);
    private static final Color ACCENT_COLOR = Color.web("#2563EB");
    private static final Background ROOT_BACKGROUND = new Background(new BackgroundFill(Color.rgb(247, 248, 251), CornerRadii.EMPTY, Insets.EMPTY));
    private static final Background CARD_BACKGROUND = new Background(new BackgroundFill(Color.WHITE, new CornerRadii(14), Insets.EMPTY));
    private static final Border CARD_BORDER = new Border(new BorderStroke(Color.rgb(227, 230, 235), BorderStrokeStyle.SOLID, new CornerRadii(14), new BorderWidths(1)));
    private static final Background ROW_BACKGROUND = new Background(new BackgroundFill(Color.rgb(251, 252, 255), new CornerRadii(8), Insets.EMPTY));
    private static final Border ROW_BORDER = new Border(new BorderStroke(Color.rgb(229, 232, 238), BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(1)));
    private static final Background ROW_HIGHLIGHT_LEFT = new Background(new BackgroundFill(Color.rgb(254, 226, 226), new CornerRadii(8), Insets.EMPTY));
    private static final Background ROW_HIGHLIGHT_RIGHT = new Background(new BackgroundFill(Color.rgb(220, 252, 231), new CornerRadii(8), Insets.EMPTY));
    private static final Border ROW_BORDER_LEFT = new Border(new BorderStroke(Color.rgb(248, 113, 113), BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(1, 1, 1, 3)));
    private static final Border ROW_BORDER_RIGHT = new Border(new BorderStroke(Color.rgb(34, 197, 94), BorderStrokeStyle.SOLID, new CornerRadii(8), new BorderWidths(1, 3, 1, 1)));
    private static final Background LINE_NUMBER_BACKGROUND = new Background(new BackgroundFill(Color.rgb(239, 241, 245), new CornerRadii(6), Insets.EMPTY));
    private static final Border LINE_NUMBER_BORDER = new Border(new BorderStroke(Color.rgb(225, 228, 233), BorderStrokeStyle.SOLID, new CornerRadii(6), new BorderWidths(1)));
    private static final Background TEXT_HIGHLIGHT_LEFT = new Background(new BackgroundFill(Color.rgb(248, 113, 113, 0.25), new CornerRadii(4), Insets.EMPTY));
    private static final Border TEXT_HIGHLIGHT_BORDER_LEFT = new Border(new BorderStroke(Color.rgb(220, 38, 38, 0.5), BorderStrokeStyle.SOLID, new CornerRadii(4), BorderWidths.DEFAULT));
    private static final Background TEXT_HIGHLIGHT_RIGHT = new Background(new BackgroundFill(Color.rgb(74, 222, 128, 0.25), new CornerRadii(4), Insets.EMPTY));
    private static final Border TEXT_HIGHLIGHT_BORDER_RIGHT = new Border(new BorderStroke(Color.rgb(22, 163, 74, 0.5), BorderStrokeStyle.SOLID, new CornerRadii(4), BorderWidths.DEFAULT));

    private final DiffViewModel viewModel = new DiffViewModel();

    private ListView<DiffEntry> leftDiffView;
    private ListView<DiffEntry> rightDiffView;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Universal Difference Checker");

        leftDiffView = createDiffListView();
        rightDiffView = createDiffListView();

        viewModel.hunksProperty().addListener((ListChangeListener<? super DiffHunk>) change -> renderDiffColumns());

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
        VBox leftCard = buildDiffCard("Left source", leftDiffView);
        VBox rightCard = buildDiffCard("Right source", rightDiffView);

        HBox content = new HBox(leftCard, rightCard);
        content.setSpacing(16);
        content.setPadding(new Insets(0, 24, 24, 24));
        HBox.setHgrow(leftCard, Priority.ALWAYS);
        HBox.setHgrow(rightCard, Priority.ALWAYS);
        return content;
    }

    private VBox buildDiffCard(String title, ListView<DiffEntry> listView) {
        Label header = new Label(title);
        header.setFont(SECTION_FONT);
        header.setTextFill(TEXT_COLOR);

        listView.setPlaceholder(createPlaceholderLabel());

        VBox card = new VBox(header, listView);
        card.setSpacing(12);
        card.setBackground(CARD_BACKGROUND);
        card.setBorder(CARD_BORDER);
        card.setPadding(new Insets(16, 20, 20, 20));
        VBox.setVgrow(listView, Priority.ALWAYS);
        return card;
    }

    private Label createPlaceholderLabel() {
        Label placeholder = new Label("Load files and run Compare to see differences.");
        placeholder.setFont(CONTROL_FONT);
        placeholder.setTextFill(MUTED_TEXT_COLOR);
        placeholder.setWrapText(true);
        placeholder.setAlignment(Pos.CENTER);
        return placeholder;
    }

    private ListView<DiffEntry> createDiffListView() {
        ListView<DiffEntry> listView = new ListView<>();
        listView.setFocusTraversable(false);
        listView.setBackground(Background.EMPTY);
        listView.setBorder(Border.EMPTY);
        listView.setCellFactory(view -> new DiffListCell());
        return listView;
    }

    private class DiffListCell extends ListCell<DiffEntry> {
        DiffListCell() {
            setPadding(new Insets(4, 0, 4, 0));
        }

        @Override
        protected void updateItem(DiffEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                return;
            }
            setGraphic(switch (entry.kind()) {
                case TEXT -> buildTextRow((TextDiffEntry) entry);
                case BINARY -> buildBinaryRow((BinaryDiffEntry) entry);
                case TRUNCATION -> buildTruncationRow((TruncationDiffEntry) entry);
            });
        }
    }

    private Node buildTextRow(TextDiffEntry entry) {
        boolean highlight = entry.line().segments().stream().anyMatch(segment -> segment.type() == SegmentType.DIFF);
        HBox row = createRowContainer(entry.side(), highlight);

        Label number = createLineNumberLabel(entry.line().number());
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(1.6);
        flow.setPrefWidth(Double.MAX_VALUE);
        flow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(flow, Priority.ALWAYS);

        if (entry.line().segments().isEmpty()) {
            flow.getChildren().add(new Text(""));
        } else {
            for (Segment segment : entry.line().segments()) {
                flow.getChildren().add(buildSegmentNode(segment, entry.side()));
            }
        }

        row.getChildren().addAll(number, flow);
        return row;
    }

    private Node buildBinaryRow(BinaryDiffEntry entry) {
        boolean highlight = entry.line().cells().stream().anyMatch(BinaryCell::diff);
        HBox row = createRowContainer(entry.side(), highlight);

        Label offset = new Label(String.format("%08X", entry.line().offset()));
        offset.setFont(MONO_LABEL_FONT);
        offset.setTextFill(MUTED_TEXT_COLOR);
        offset.setBackground(LINE_NUMBER_BACKGROUND);
        offset.setBorder(LINE_NUMBER_BORDER);
        offset.setPadding(new Insets(2, 12, 2, 12));

        TextFlow hexFlow = new TextFlow();
        hexFlow.setLineSpacing(2);
        TextFlow asciiFlow = new TextFlow();
        asciiFlow.setLineSpacing(2);

        for (BinaryCell cell : entry.line().cells()) {
            Label hexLabel = new Label(cell.hex());
            hexLabel.setFont(MONO_FONT);
            hexLabel.setPadding(new Insets(0, 6, 0, 0));

            Label asciiLabel = new Label(cell.present() ? String.valueOf(cell.ascii()) : " ");
            asciiLabel.setFont(MONO_FONT);
            asciiLabel.setPadding(new Insets(0, 3, 0, 0));

            if (cell.diff() && cell.present()) {
                applyBinaryHighlight(hexLabel, entry.side());
                applyBinaryHighlight(asciiLabel, entry.side());
            }

            hexFlow.getChildren().add(hexLabel);
            asciiFlow.getChildren().add(asciiLabel);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(offset, hexFlow, spacer, asciiFlow);
        return row;
    }

    private Node buildTruncationRow(TruncationDiffEntry entry) {
        HBox row = createRowContainer(null, false);

        Label badge = new Label("...");
        badge.setFont(MONO_LABEL_FONT);
        badge.setTextFill(MUTED_TEXT_COLOR);
        badge.setBackground(LINE_NUMBER_BACKGROUND);
        badge.setBorder(LINE_NUMBER_BORDER);
        badge.setPadding(new Insets(2, 8, 2, 8));

        String unit = entry.binary() ? "bytes" : "characters";
        String messageText = String.format("Showing first %,d of %,d %s for performance.", entry.shown(), entry.total(), unit);
        Label message = new Label(messageText);
        message.setFont(CONTROL_FONT);
        message.setWrapText(true);
        message.setTextFill(MUTED_TEXT_COLOR);

        row.getChildren().addAll(badge, message);
        return row;
    }

    private HBox createRowContainer(DiffSide side, boolean emphasize) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setSpacing(12);
        row.setPadding(new Insets(6, 10, 6, 10));
        if (!emphasize || side == null) {
            row.setBackground(ROW_BACKGROUND);
            row.setBorder(ROW_BORDER);
        } else if (side == DiffSide.LEFT) {
            row.setBackground(ROW_HIGHLIGHT_LEFT);
            row.setBorder(ROW_BORDER_LEFT);
        } else {
            row.setBackground(ROW_HIGHLIGHT_RIGHT);
            row.setBorder(ROW_BORDER_RIGHT);
        }
        return row;
    }

    private Label createLineNumberLabel(int number) {
        Label label = new Label(String.valueOf(number));
        label.setFont(MONO_LABEL_FONT);
        label.setTextFill(MUTED_TEXT_COLOR);
        label.setBackground(LINE_NUMBER_BACKGROUND);
        label.setBorder(LINE_NUMBER_BORDER);
        label.setPadding(new Insets(2, 8, 2, 8));
        label.setAlignment(Pos.CENTER_RIGHT);
        label.setMinWidth(60);
        return label;
    }

    private Node buildSegmentNode(Segment segment, DiffSide side) {
        if (segment.type() == SegmentType.DIFF) {
            Label highlight = new Label(segment.text());
            highlight.setFont(MONO_FONT);
            highlight.setPadding(new Insets(0, 4, 0, 4));
            if (side == DiffSide.LEFT) {
                highlight.setBackground(TEXT_HIGHLIGHT_LEFT);
                highlight.setBorder(TEXT_HIGHLIGHT_BORDER_LEFT);
            } else {
                highlight.setBackground(TEXT_HIGHLIGHT_RIGHT);
                highlight.setBorder(TEXT_HIGHLIGHT_BORDER_RIGHT);
            }
            return highlight;
        }
        Text text = new Text(segment.text());
        text.setFont(MONO_FONT);
        text.setFill(TEXT_COLOR);
        return text;
    }

    private void applyBinaryHighlight(Label label, DiffSide side) {
        if (side == DiffSide.LEFT) {
            label.setBackground(TEXT_HIGHLIGHT_LEFT);
            label.setBorder(TEXT_HIGHLIGHT_BORDER_LEFT);
        } else {
            label.setBackground(TEXT_HIGHLIGHT_RIGHT);
            label.setBorder(TEXT_HIGHLIGHT_BORDER_RIGHT);
        }
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
            log.error("Comparison failed while reading files {} and {}", viewModel.leftPathProperty().get(), viewModel.rightPathProperty().get(), ex);
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
        Optional<ComparisonSession> sessionOpt = viewModel.getCurrentSession();
        if (sessionOpt.isEmpty()) {
            return;
        }

        try {
            ComparisonSession session = sessionOpt.get();
            FormatType formatType = session.getLeft().getFormatType();
            boolean isBinary = isBinaryFormat(formatType);

            if (isBinary) {
                BinarySlice leftSlice = readBinarySlice(session.getLeft().getPath());
                BinarySlice rightSlice = readBinarySlice(session.getRight().getPath());

                List<DiffEntry> leftEntries = new ArrayList<>();
                leftEntries.addAll(toBinaryEntries(buildBinaryLines(leftSlice.bytes(), rightSlice.bytes()), DiffSide.LEFT));
                if (leftSlice.truncated()) {
                    leftEntries.add(new TruncationDiffEntry(true, leftSlice.totalLength(), leftSlice.bytes().length));
                }

                List<DiffEntry> rightEntries = new ArrayList<>();
                rightEntries.addAll(toBinaryEntries(buildBinaryLines(rightSlice.bytes(), leftSlice.bytes()), DiffSide.RIGHT));
                if (rightSlice.truncated()) {
                    rightEntries.add(new TruncationDiffEntry(true, rightSlice.totalLength(), rightSlice.bytes().length));
                }

                leftDiffView.getItems().setAll(leftEntries);
                rightDiffView.getItems().setAll(rightEntries);
                return;
            }

            String leftText = String.join("\n", session.getLeftContent().getLogicalRecords());
            String rightText = String.join("\n", session.getRightContent().getLogicalRecords());

            boolean leftTruncated = leftText.length() > TEXT_RENDER_LIMIT;
            boolean rightTruncated = rightText.length() > TEXT_RENDER_LIMIT;
            String leftShown = leftTruncated ? leftText.substring(0, TEXT_RENDER_LIMIT) : leftText;
            String rightShown = rightTruncated ? rightText.substring(0, TEXT_RENDER_LIMIT) : rightText;

            List<DiffEntry> leftEntries = new ArrayList<>();
            leftEntries.addAll(toTextEntries(buildTextLines(leftShown, rightShown), DiffSide.LEFT));
            if (leftTruncated) {
                leftEntries.add(new TruncationDiffEntry(false, leftText.length(), leftShown.length()));
            }

            List<DiffEntry> rightEntries = new ArrayList<>();
            rightEntries.addAll(toTextEntries(buildTextLines(rightShown, leftShown), DiffSide.RIGHT));
            if (rightTruncated) {
                rightEntries.add(new TruncationDiffEntry(false, rightText.length(), rightShown.length()));
            }

            leftDiffView.getItems().setAll(leftEntries);
            rightDiffView.getItems().setAll(rightEntries);
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

    private List<DiffEntry> toTextEntries(List<TextLine> lines, DiffSide side) {
        List<DiffEntry> entries = new ArrayList<>();
        for (TextLine line : lines) {
            entries.add(new TextDiffEntry(line, side));
        }
        return entries;
    }

    private List<DiffEntry> toBinaryEntries(List<BinaryLine> lines, DiffSide side) {
        List<DiffEntry> entries = new ArrayList<>();
        for (BinaryLine line : lines) {
            entries.add(new BinaryDiffEntry(line, side));
        }
        return entries;
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

    private enum RowKind {
        TEXT,
        BINARY,
        TRUNCATION
    }

    private interface DiffEntry {
        RowKind kind();
    }

    private record Segment(String text, SegmentType type) {
    }

    private record TextLine(int number, List<Segment> segments) {
    }

    private record BinaryCell(int index, String hex, char ascii, boolean diff, boolean present) {
    }

    private record BinaryLine(int offset, List<BinaryCell> cells) {
    }

    private record TextDiffEntry(TextLine line, DiffSide side) implements DiffEntry {
        @Override
        public RowKind kind() {
            return RowKind.TEXT;
        }
    }

    private record BinaryDiffEntry(BinaryLine line, DiffSide side) implements DiffEntry {
        @Override
        public RowKind kind() {
            return RowKind.BINARY;
        }
    }

    private record TruncationDiffEntry(boolean binary, long total, long shown) implements DiffEntry {
        @Override
        public RowKind kind() {
            return RowKind.TRUNCATION;
        }
    }

    private record BinarySlice(byte[] bytes, long totalLength) {
        boolean truncated() {
            return totalLength > bytes.length;
        }
    }
}
