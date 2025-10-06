package com.universaldiff.core.model;

import java.util.Objects;

public final class MergeDecision {
    private final String hunkId;
    private final MergeChoice choice;
    private final String manualContent;

    public MergeDecision(String hunkId, MergeChoice choice, String manualContent) {
        this.hunkId = Objects.requireNonNull(hunkId, "hunkId");
        this.choice = Objects.requireNonNull(choice, "choice");
        this.manualContent = manualContent;
    }

    public String getHunkId() {
        return hunkId;
    }

    public MergeChoice getChoice() {
        return choice;
    }

    public String getManualContent() {
        return manualContent;
    }
}
