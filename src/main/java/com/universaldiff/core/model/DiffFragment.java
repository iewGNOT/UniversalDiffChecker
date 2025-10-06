package com.universaldiff.core.model;

import java.util.Objects;

/**
 * Represents a fragment of content participating in a diff hunk.
 */
public final class DiffFragment {
    private final DiffSide side;
    private final int start;
    private final int end;
    private final String content;

    public DiffFragment(DiffSide side, int start, int end, String content) {
        this.side = Objects.requireNonNull(side, "side");
        this.start = start;
        this.end = end;
        this.content = content == null ? "" : content;
    }

    public DiffSide getSide() {
        return side;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public String getContent() {
        return content;
    }
}
