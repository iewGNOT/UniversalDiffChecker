package com.universaldiff.core.model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public final class MergeResult {
    private final FormatType formatType;
    private final Path outputPath;
    private final Duration executionTime;

    public MergeResult(FormatType formatType, Path outputPath, Duration executionTime) {
        this.formatType = Objects.requireNonNull(formatType, "formatType");
        this.outputPath = outputPath;
        this.executionTime = executionTime == null ? Duration.ZERO : executionTime;
    }

    public FormatType getFormatType() {
        return formatType;
    }

    public Path getOutputPath() {
        return outputPath;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }
}
