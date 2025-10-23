package com.universaldiff.core;

import com.universaldiff.core.io.EncodingDetector;
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

public final class ComparisonService {
    private final FileLoader fileLoader;
    private final FormatAdapterRegistry registry;

    public ComparisonService(FileLoader fileLoader, FormatAdapterRegistry registry) {
        this.fileLoader = Objects.requireNonNull(fileLoader, "fileLoader");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public ComparisonSession compare(Path leftPath, Path rightPath) throws IOException {
        return compare(leftPath, rightPath, ComparisonOptions.builder().build());
    }

    public ComparisonSession compare(Path leftPath, Path rightPath, ComparisonOptions options) throws IOException {
        Objects.requireNonNull(leftPath, "leftPath");
        Objects.requireNonNull(rightPath, "rightPath");
        Objects.requireNonNull(options, "options");

        FileDescriptor leftDescriptor = applyOverrides(fileLoader.detect(leftPath), options.leftEncodingOverride());
        FileDescriptor rightDescriptor = applyOverrides(fileLoader.detect(rightPath), options.rightEncodingOverride());

        FormatType format = options.forcedFormat()
                .orElse(leftDescriptor.getFormatType());
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

    private FileDescriptor applyOverrides(FileDescriptor descriptor, java.util.Optional<Charset> charsetOverride) {
        if (charsetOverride.isPresent()) {
            return new FileDescriptor(descriptor.getPath(), descriptor.getFormatType(), charsetOverride.get());
        }
        return descriptor;
    }

    public static ComparisonService createDefault() {
        return createDefault(true);
    }

    public static ComparisonService createDefault(boolean ignoreJsonKeyOrder) {
        FormatAdapterRegistry registry = new FormatAdapterRegistry();
        registry.register(FormatType.TXT, new com.universaldiff.format.txt.TxtFormatAdapter());
        registry.register(FormatType.CSV, new com.universaldiff.format.csv.CsvFormatAdapter());
        registry.register(FormatType.JSON, new com.universaldiff.format.json.JsonFormatAdapter(ignoreJsonKeyOrder));
        registry.register(FormatType.XML, new com.universaldiff.format.xml.XmlFormatAdapter());
        registry.register(FormatType.BIN, new com.universaldiff.format.bin.BinaryFormatAdapter(FormatType.BIN));
        registry.register(FormatType.HEX, new com.universaldiff.format.bin.BinaryFormatAdapter(FormatType.HEX));
        FileLoader loader = new FileLoader(new com.universaldiff.core.detect.DefaultFileTypeDetector(), new EncodingDetector());
        return new ComparisonService(loader, registry);
    }
}

