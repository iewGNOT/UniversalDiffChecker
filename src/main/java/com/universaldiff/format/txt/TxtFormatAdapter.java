package com.universaldiff.format.txt;

import com.universaldiff.core.model.DiffFragment;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.DiffSide;
import com.universaldiff.core.model.DiffType;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeChoice;
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
import java.util.List;

public class TxtFormatAdapter implements FormatAdapter {

    @Override
    public NormalizedContent normalize(FileDescriptor descriptor) throws IOException {
        Charset encoding = descriptor.getEncoding();
        List<String> lines = Files.readAllLines(descriptor.getPath(), encoding)
                .stream()
                .map(this::normalizeLine)
                .toList();
        return NormalizedContent.builder(FormatType.TXT)
                .logicalRecords(lines)
                .encoding(encoding)
                .build();
    }

    private String normalizeLine(String line) {
        return line.replace("\t", "    ");
    }

    @Override
    public DiffResult diff(NormalizedContent left, NormalizedContent right) {
        Instant start = Instant.now();
        List<String> leftLines = left.getLogicalRecords();
        List<String> rightLines = right.getLogicalRecords();
        List<DiffHunk> hunks = new ArrayList<>();
        int max = Math.max(leftLines.size(), rightLines.size());
        for (int i = 0; i < max; i++) {
            String leftValue = i < leftLines.size() ? leftLines.get(i) : null;
            String rightValue = i < rightLines.size() ? rightLines.get(i) : null;
            if (leftValue != null && rightValue != null) {
                if (!leftValue.equals(rightValue)) {
                    hunks.add(DiffHunk.of(
                            "txt-line-" + (i + 1),
                            DiffType.MODIFY,
                            "Line " + (i + 1),
                            List.of(
                                    new DiffFragment(DiffSide.LEFT, i, i, leftValue),
                                    new DiffFragment(DiffSide.RIGHT, i, i, rightValue)
                            )));
                }
            } else if (leftValue == null && rightValue != null) {
                hunks.add(DiffHunk.of(
                        "txt-line-" + (i + 1),
                        DiffType.INSERT,
                        "Insert at line " + (i + 1),
                        List.of(new DiffFragment(DiffSide.RIGHT, i, i, rightValue))));
            } else if (leftValue != null) {
                hunks.add(DiffHunk.of(
                        "txt-line-" + (i + 1),
                        DiffType.DELETE,
                        "Delete line " + (i + 1),
                        List.of(new DiffFragment(DiffSide.LEFT, i, i, leftValue))));
            }
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
        for (MergeDecision decision : decisions) {
            int index = extractIndex(decision.getHunkId());
            if (index < 0) {
                continue;
            }
            switch (decision.getChoice()) {
                case TAKE_LEFT -> applyLeft(merged, left.getLogicalRecords(), index);
                case TAKE_RIGHT -> applyRight(merged, right.getLogicalRecords(), index);
                case MANUAL -> applyManual(merged, decision.getManualContent(), index);
            }
        }
        if (outputPath != null) {
            Files.write(outputPath, merged, left.getEncoding());
        }
        return new MergeResult(FormatType.TXT, outputPath, Duration.between(start, Instant.now()));
    }

    private void applyLeft(List<String> target, List<String> left, int index) {
        if (index < target.size() && index < left.size()) {
            target.set(index, left.get(index));
        }
    }

    private void applyRight(List<String> target, List<String> right, int index) {
        if (index < right.size()) {
            if (index < target.size()) {
                target.set(index, right.get(index));
            } else {
                target.add(right.get(index));
            }
        }
    }

    private void applyManual(List<String> target, String manualContent, int index) {
        if (manualContent == null) {
            return;
        }
        if (index < target.size()) {
            target.set(index, manualContent);
        } else {
            target.add(manualContent);
        }
    }

    private int extractIndex(String hunkId) {
        if (hunkId == null) {
            return -1;
        }
        try {
            return Integer.parseInt(hunkId.replace("txt-line-", "")) - 1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}

