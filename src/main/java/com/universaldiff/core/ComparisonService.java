package com.universaldiff.core;

import com.universaldiff.core.detect.DefaultFileTypeDetector;
import com.universaldiff.core.io.BomEncodingDetector;
import com.universaldiff.core.io.DefaultFileLoader;
import com.universaldiff.core.io.FileLoader;
import com.universaldiff.core.model.ComparisonOptions;
import com.universaldiff.core.model.ComparisonSession;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.format.spi.FormatAdapterRegistry;
import com.universaldiff.format.spi.InMemoryFormatAdapterRegistry;

import java.io.IOException;
import java.nio.file.Path;

public interface ComparisonService {

    ComparisonSession compare(Path leftPath, Path rightPath) throws IOException;

    ComparisonSession compare(Path leftPath, Path rightPath, ComparisonOptions options) throws IOException;

    static ComparisonService createDefault() {
        return createDefault(true);
    }

    static ComparisonService createDefault(boolean ignoreJsonKeyOrder) {
        FormatAdapterRegistry registry = new InMemoryFormatAdapterRegistry();
        com.universaldiff.format.txt.TxtFormatAdapter txtAdapter = new com.universaldiff.format.txt.TxtFormatAdapter();
        registry.register(FormatType.TXT, txtAdapter);
        registry.register(FormatType.CSV, txtAdapter);
        registry.register(FormatType.JSON, txtAdapter);
        registry.register(FormatType.XML, txtAdapter);
        registry.register(FormatType.BIN, new com.universaldiff.format.bin.BinaryFormatAdapter(FormatType.BIN));
        registry.register(FormatType.HEX, new com.universaldiff.format.bin.BinaryFormatAdapter(FormatType.HEX));

        FileLoader loader = new DefaultFileLoader(new DefaultFileTypeDetector(), new BomEncodingDetector());
        return new DefaultComparisonService(loader, registry);
    }
}

