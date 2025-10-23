package com.universaldiff.format.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import com.universaldiff.format.json.spi.JsonDiffer;
import com.universaldiff.format.json.spi.JsonMerger;
import com.universaldiff.format.json.spi.JsonNormalizer;
import com.universaldiff.format.spi.FormatAdapter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Facade {@link FormatAdapter} that delegates JSON responsibilities to composable collaborators.
 * The collaborators encapsulate parsing, diffing, and merging to keep this surface area compact.
 */
public final class JsonFormatAdapter implements FormatAdapter {

    private final JsonNormalizer normalizer;
    private final JsonDiffer differ;
    private final JsonMerger merger;

    public JsonFormatAdapter() {
        this(true);
    }

    public JsonFormatAdapter(boolean ignoreKeyOrder) {
        ObjectMapper mapper = new ObjectMapper();
        this.normalizer = new JacksonTreeJsonNormalizer(mapper, ignoreKeyOrder);
        this.differ = new PathAwareJsonDiffer();
        this.merger = new PointerAwareJsonMerger(mapper);
    }

    public JsonFormatAdapter(JsonNormalizer normalizer, JsonDiffer differ, JsonMerger merger) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.differ = Objects.requireNonNull(differ, "differ");
        this.merger = Objects.requireNonNull(merger, "merger");
    }

    @Override
    public NormalizedContent normalize(FileDescriptor descriptor) throws IOException {
        return normalizer.normalize(descriptor);
    }

    @Override
    public DiffResult diff(NormalizedContent left, NormalizedContent right) {
        return differ.diff(left, right);
    }

    @Override
    public MergeResult merge(NormalizedContent left,
                             NormalizedContent right,
                             List<MergeDecision> decisions,
                             Path outputPath) throws IOException {
        return merger.merge(left, right, decisions, outputPath);
    }
}
