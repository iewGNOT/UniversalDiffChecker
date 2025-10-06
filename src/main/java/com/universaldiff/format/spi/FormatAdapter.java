package com.universaldiff.format.spi;

import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;

import java.io.IOException;
import java.util.List;

public interface FormatAdapter {
    NormalizedContent normalize(FileDescriptor descriptor) throws IOException;

    DiffResult diff(NormalizedContent left, NormalizedContent right) throws IOException;

    MergeResult merge(NormalizedContent left,
                      NormalizedContent right,
                      List<MergeDecision> decisions,
                      java.nio.file.Path outputPath) throws IOException;

    default boolean supportsMergePreview() {
        return true;
    }
}
