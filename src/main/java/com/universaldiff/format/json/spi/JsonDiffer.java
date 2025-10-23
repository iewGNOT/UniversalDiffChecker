package com.universaldiff.format.json.spi;

import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.NormalizedContent;

/**
 * Produces structural JSON diffs between two normalized documents.
 * <p>
 * Responsibilities:
 * - Compare logical record maps produced by {@link JsonNormalizer}.
 * - Generate {@link DiffResult} instances that capture path-based changes.
 * <p>
 * Invariants:
 * - Accepts inputs that originated from the same {@link JsonNormalizer} configuration.
 * - Never mutates the supplied {@link NormalizedContent} instances.
 */
public interface JsonDiffer {

    DiffResult diff(NormalizedContent left, NormalizedContent right);
}
