package com.universaldiff.format.txt;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.universaldiff.core.model.DiffFragment;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.DiffSide;
import com.universaldiff.core.model.DiffType;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import com.universaldiff.format.spi.FormatAdapter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class TxtFormatAdapter implements FormatAdapter {

    @Override
    public NormalizedContent normalize(FileDescriptor descriptor) throws IOException {
        Charset encoding = descriptor.getEncoding();
        List<String> lines = new ArrayList<>();
        try (Stream<String> stream = Files.lines(descriptor.getPath(), encoding)) {
            stream.forEach(lines::add);
        }
        return NormalizedContent.builder(FormatType.TXT)
                .logicalRecords(lines)
                .encoding(encoding)
                .build();
    }

    @Override
    public DiffResult diff(NormalizedContent left, NormalizedContent right) {
        Instant start = Instant.now();
        List<String> leftLines = left.getLogicalRecords();
        List<String> rightLines = right.getLogicalRecords();
        List<DiffHunk> hunks = new ArrayList<>();
        for (DeltaInfo delta : calculateDeltas(leftLines, rightLines)) {
            List<DiffFragment> fragments = new ArrayList<>();
            if (!delta.sourceLines().isEmpty()) {
                fragments.add(new DiffFragment(
                        DiffSide.LEFT,
                        delta.sourcePos(),
                        delta.sourcePos() + delta.sourceLines().size() - 1,
                        renderLines(delta.sourceLines())));
            }
            if (!delta.targetLines().isEmpty()) {
                fragments.add(new DiffFragment(
                        DiffSide.RIGHT,
                        delta.targetPos(),
                        delta.targetPos() + delta.targetLines().size() - 1,
                        renderLines(delta.targetLines())));
            }
            hunks.add(DiffHunk.of(
                    delta.id(),
                    delta.type(),
                    buildSummary(delta),
                    fragments));
        }
        return new DiffResult(FormatType.TXT, hunks, Duration.between(start, Instant.now()));
    }

    @Override
    public MergeResult merge(NormalizedContent left,
                             NormalizedContent right,
                             List<MergeDecision> decisions,
                             Path outputPath) throws IOException {
        Instant start = Instant.now();
        List<String> merged = new ArrayList<>(left.getLogicalRecords());
        Map<String, MergeDecision> decisionIndex = new LinkedHashMap<>();
        for (MergeDecision decision : decisions) {
            decisionIndex.put(decision.getHunkId(), decision);
        }
        List<DeltaInfo> deltas = calculateDeltas(left.getLogicalRecords(), right.getLogicalRecords());
        int offset = 0;
        for (DeltaInfo delta : deltas) {
            MergeDecision decision = decisionIndex.get(delta.id());
            if (decision == null) {
                continue;
            }
            int index = delta.sourcePos() + offset;
            switch (decision.getChoice()) {
                case TAKE_LEFT -> {
                    // Default view already reflects left side; no action needed.
                }
                case TAKE_RIGHT -> offset += applyRight(merged, delta, index);
                case MANUAL -> offset += applyManual(merged, delta, index, decision.getManualContent());
            }
        }
        if (outputPath != null) {
            Files.write(outputPath, merged, left.getEncoding());
        }
        return new MergeResult(FormatType.TXT, outputPath, Duration.between(start, Instant.now()));
    }

    private int applyRight(List<String> target, DeltaInfo delta, int index) {
        int before = target.size();
        int insertionIndex = clampIndex(index, target.size());
        if (delta.type() == DiffType.INSERT) {
            if (!delta.targetLines().isEmpty()) {
                target.addAll(insertionIndex, delta.targetLines());
            }
            return target.size() - before;
        }
        replaceRange(target, insertionIndex, delta.sourceLines().size(), delta.targetLines());
        return target.size() - before;
    }

    private int applyManual(List<String> target, DeltaInfo delta, int index, String manualContent) {
        if (manualContent == null) {
            return 0;
        }
        List<String> manualLines = splitManualContent(manualContent);
        int before = target.size();
        int insertionIndex = clampIndex(index, target.size());
        if (delta.type() != DiffType.INSERT) {
            replaceRange(target, insertionIndex, delta.sourceLines().size(), manualLines);
        } else {
            if (!manualLines.isEmpty()) {
                target.addAll(insertionIndex, manualLines);
            }
        }
        return target.size() - before;
    }

    private List<String> splitManualContent(String manualContent) {
        if (manualContent.isEmpty()) {
            return List.of("");
        }
        return List.of(manualContent.split("\\R", -1));
    }

    private void replaceRange(List<String> target, int index, int length, List<String> replacement) {
        int start = clampIndex(index, target.size());
        int actualLength = Math.min(length, Math.max(0, target.size() - start));
        for (int i = 0; i < actualLength && start < target.size(); i++) {
            target.remove(start);
        }
        if (replacement != null && !replacement.isEmpty()) {
            target.addAll(start, replacement);
        }
    }

    private int clampIndex(int index, int size) {
        if (index < 0) {
            return 0;
        }
        return Math.min(index, size);
    }

    private List<DeltaInfo> calculateDeltas(List<String> leftLines, List<String> rightLines) {
        Patch<String> patch = DiffUtils.diff(leftLines, rightLines);
        List<DeltaInfo> deltas = new ArrayList<>();
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            DiffType type = mapType(delta.getType());
            if (type == null) {
                continue;
            }
            switch (type) {
                case INSERT, DELETE -> deltas.addAll(expandUniformDelta(delta, type));
                case MODIFY -> deltas.addAll(expandModifyDelta(delta));
                case EQUAL -> {
                    // no-op; DiffUtils never emits EQUAL deltas
                }
            }
        }
        return deltas;
    }

    private List<DeltaInfo> expandUniformDelta(AbstractDelta<String> delta, DiffType type) {
        List<DeltaInfo> expanded = new ArrayList<>();
        List<String> lines = type == DiffType.INSERT
                ? List.copyOf(delta.getTarget().getLines())
                : List.copyOf(delta.getSource().getLines());
        int sourceStart = delta.getSource().getPosition();
        int targetStart = delta.getTarget().getPosition();
        for (int i = 0; i < lines.size(); i++) {
            int sourcePos = sourceStart + i;
            int targetPos = targetStart + i;
            List<String> sourceLines = type == DiffType.INSERT ? List.of() : List.of(lines.get(i));
            List<String> targetLines = type == DiffType.INSERT ? List.of(lines.get(i)) : List.of();
            expanded.add(new DeltaInfo(
                    buildHunkId(sourcePos),
                    type,
                    sourcePos,
                    sourceLines,
                    targetPos,
                    targetLines));
        }
        return expanded;
    }

    private List<DeltaInfo> expandModifyDelta(AbstractDelta<String> delta) {
        List<DeltaInfo> expanded = new ArrayList<>();
        List<String> sourceLines = List.copyOf(delta.getSource().getLines());
        List<String> targetLines = List.copyOf(delta.getTarget().getLines());
        int sourceStart = delta.getSource().getPosition();
        int targetStart = delta.getTarget().getPosition();
        int span = Math.max(sourceLines.size(), targetLines.size());
        for (int i = 0; i < span; i++) {
            String leftLine = i < sourceLines.size() ? sourceLines.get(i) : null;
            String rightLine = i < targetLines.size() ? targetLines.get(i) : null;
            if (leftLine != null && rightLine != null) {
                expanded.add(new DeltaInfo(
                        buildHunkId(sourceStart + i),
                        DiffType.MODIFY,
                        sourceStart + i,
                        List.of(leftLine),
                        targetStart + i,
                        List.of(rightLine)));
            } else if (leftLine == null && rightLine != null) {
                expanded.add(new DeltaInfo(
                        buildHunkId(sourceStart + i),
                        DiffType.INSERT,
                        sourceStart + i,
                        List.of(),
                        targetStart + i,
                        List.of(rightLine)));
            } else if (leftLine != null) {
                expanded.add(new DeltaInfo(
                        buildHunkId(sourceStart + i),
                        DiffType.DELETE,
                        sourceStart + i,
                        List.of(leftLine),
                        targetStart + Math.min(i, targetLines.size()),
                        List.of()));
            }
        }
        return expanded;
    }

    private String buildHunkId(int sourcePos) {
        return "txt-line-" + (sourcePos + 1);
    }

    private DiffType mapType(DeltaType type) {
        return switch (type) {
            case DELETE -> DiffType.DELETE;
            case INSERT -> DiffType.INSERT;
            case CHANGE -> DiffType.MODIFY;
            default -> null;
        };
    }

    private String buildSummary(DeltaInfo delta) {
        return switch (delta.type()) {
            case INSERT -> "Insert at line " + (delta.sourcePos() + 1);
            case DELETE -> "Delete " + describeRange(delta.sourcePos(), delta.sourceLines().size());
            case MODIFY -> "Modify " + describeRange(delta.sourcePos(), delta.sourceLines().size());
            case EQUAL -> "No change at line " + (delta.sourcePos() + 1);
        };
    }

    private String describeRange(int start, int length) {
        int from = start + 1;
        int to = start + Math.max(length, 1);
        if (from == to) {
            return "line " + from;
        }
        return "lines " + from + "-" + to;
    }

    private String renderLines(List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        return String.join(System.lineSeparator(), lines);
    }

    private record DeltaInfo(String id,
                             DiffType type,
                             int sourcePos,
                             List<String> sourceLines,
                             int targetPos,
                             List<String> targetLines) {
        DeltaInfo {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(sourceLines, "sourceLines");
            Objects.requireNonNull(targetLines, "targetLines");
        }
    }
}

