package com.universaldiff.core.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DiffResult {
    private final FormatType formatType;
    private final List<DiffHunk> hunks;
    private final Duration executionTime;

    public DiffResult(FormatType formatType, List<DiffHunk> hunks, Duration executionTime) {
        this.formatType = Objects.requireNonNull(formatType, "formatType");
        this.hunks = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(hunks, "hunks")));
        this.executionTime = executionTime == null ? Duration.ZERO : executionTime;
    }

    public FormatType getFormatType() {
        return formatType;
    }

    public List<DiffHunk> getHunks() {
        return hunks;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    public boolean isEmpty() {
        return hunks.isEmpty();
    }
}
