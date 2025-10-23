package com.universaldiff.format.json.spi;

import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.NormalizedContent;

import java.io.IOException;

/**
 * Converts raw JSON descriptors into {@link NormalizedContent} representations.
 * <p>
 * Responsibilities:
 * - Parse the JSON file into an in-memory model.
 * - Produce a stable logical record representation suitable for diffing.
 * - Retain the original character encoding for round-tripping.
 * <p>
 * Invariants:
 * - Returned content always declares {@link NormalizedContent#getFormatType()} as {@link com.universaldiff.core.model.FormatType#JSON}.
 * - The native model stored inside the content is a Jackson {@code JsonNode}.
 */
public interface JsonNormalizer {

    NormalizedContent normalize(FileDescriptor descriptor) throws IOException;
}
