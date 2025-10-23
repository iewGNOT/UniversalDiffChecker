package com.universaldiff.core.model;

import com.universaldiff.format.spi.FormatAdapter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ComparisonSession {
    private final FileDescriptor left;
    private final FileDescriptor right;
    private final NormalizedContent leftContent;
    private final NormalizedContent rightContent;
    private final DiffResult diffResult;
    private final FormatAdapter adapter;

    public ComparisonSession(FileDescriptor left,
                             FileDescriptor right,
                             NormalizedContent leftContent,
                             NormalizedContent rightContent,
                             DiffResult diffResult,
                             FormatAdapter adapter) {
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
        this.leftContent = Objects.requireNonNull(leftContent, "leftContent");
        this.rightContent = Objects.requireNonNull(rightContent, "rightContent");
        this.diffResult = Objects.requireNonNull(diffResult, "diffResult");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    public FileDescriptor getLeft() {
        return left;
    }

    public FileDescriptor getRight() {
        return right;
    }

    public NormalizedContent getLeftContent() {
        return leftContent;
    }

    public NormalizedContent getRightContent() {
        return rightContent;
    }

    public DiffResult getDiffResult() {
        return diffResult;
    }

    public MergeResult merge(List<MergeDecision> decisions, Path outputPath) throws IOException {
        return adapter.merge(leftContent, rightContent, decisions, outputPath);
    }
}
