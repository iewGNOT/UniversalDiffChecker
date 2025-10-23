package com.universaldiff.core;

import com.universaldiff.core.io.FileLoader;
import com.universaldiff.core.model.ComparisonOptions;
import com.universaldiff.core.model.ComparisonSession;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.NormalizedContent;
import com.universaldiff.format.spi.FormatAdapter;
import com.universaldiff.format.spi.FormatAdapterRegistry;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

final class DefaultComparisonService implements ComparisonService {

    private final FileLoader fileLoader;
    private final FormatAdapterRegistry registry;

    DefaultComparisonService(FileLoader fileLoader, FormatAdapterRegistry registry) {
        this.fileLoader = Objects.requireNonNull(fileLoader, "fileLoader");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public ComparisonSession compare(Path leftPath, Path rightPath) throws IOException {
        return compare(leftPath, rightPath, ComparisonOptions.builder().build());
    }

    @Override
    public ComparisonSession compare(Path leftPath, Path rightPath, ComparisonOptions options) throws IOException {
        Objects.requireNonNull(leftPath, "leftPath");
        Objects.requireNonNull(rightPath, "rightPath");
        Objects.requireNonNull(options, "options");

        FileDescriptor leftDescriptor = applyOverrides(fileLoader.detect(leftPath), options.leftEncodingOverride());
        FileDescriptor rightDescriptor = applyOverrides(fileLoader.detect(rightPath), options.rightEncodingOverride());

        FormatType format = options.forcedFormat().orElse(leftDescriptor.getFormatType());
        if (format == FormatType.UNKNOWN) {
            throw new IOException("Unable to detect file format. Please supply an override.");
        }
        if (format != rightDescriptor.getFormatType()) {
            if (options.forcedFormat().isEmpty()) {
                throw new IOException("Files do not share the same detected format: "
                        + leftDescriptor.getFormatType() + " vs " + rightDescriptor.getFormatType());
            }
            rightDescriptor = new FileDescriptor(rightDescriptor.getPath(), format, rightDescriptor.getEncoding());
            leftDescriptor = new FileDescriptor(leftDescriptor.getPath(), format, leftDescriptor.getEncoding());
        }

        FormatAdapter adapter = registry.getAdapter(format);
        NormalizedContent leftContent = adapter.normalize(leftDescriptor);
        NormalizedContent rightContent = adapter.normalize(rightDescriptor);
        DiffResult diffResult = adapter.diff(leftContent, rightContent);
        return new ComparisonSession(leftDescriptor, rightDescriptor, leftContent, rightContent, diffResult, adapter);
    }

    private FileDescriptor applyOverrides(FileDescriptor descriptor, Optional<Charset> charsetOverride) {
        if (charsetOverride.isPresent()) {
            return new FileDescriptor(descriptor.getPath(), descriptor.getFormatType(), charsetOverride.get());
        }
        return descriptor;
    }
}
