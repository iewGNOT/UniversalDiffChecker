package com.universaldiff.format.json.spi;

import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Applies merge decisions against normalized JSON content and optionally writes the merged file.
 * <p>
 * Responsibilities:
 * - Respect {@link MergeDecision} choices, including manual overrides.
 * - Persist results when an {@link Path} is provided by the caller.
 * <p>
 * Invariants:
 * - Consumes decisions created for the same diff run that produced the supplied contents.
 * - Does not modify the input {@link NormalizedContent} instances.
 */
public interface JsonMerger {

    MergeResult merge(NormalizedContent left,
                      NormalizedContent right,
                      List<MergeDecision> decisions,
                      Path outputPath) throws IOException;
}
