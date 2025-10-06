package com.universaldiff.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A diff hunk groups related fragments and metadata for UI presentation and merge decisions.
 */
public final class DiffHunk {
    private final String id;
    private final DiffType type;
    private final String summary;
    private final List<DiffFragment> fragments;

    private DiffHunk(String id, DiffType type, String summary, List<DiffFragment> fragments) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.summary = summary == null ? "" : summary;
        this.fragments = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(fragments, "fragments")));
    }

    public String getId() {
        return id;
    }

    public DiffType getType() {
        return type;
    }

    public String getSummary() {
        return summary;
    }

    public List<DiffFragment> getFragments() {
        return fragments;
    }

    public static DiffHunk of(DiffType type, String summary, List<DiffFragment> fragments) {
        return new DiffHunk(UUID.randomUUID().toString(), type, summary, fragments);
    }

    public static DiffHunk of(String id, DiffType type, String summary, List<DiffFragment> fragments) {
        return new DiffHunk(id, type, summary, fragments);
    }
}
