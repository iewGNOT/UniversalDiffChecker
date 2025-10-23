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
        registry.register(FormatType.TXT, new com.universaldiff.format.txt.TxtFormatAdapter());
        registry.register(FormatType.CSV, new com.universaldiff.format.csv.CsvFormatAdapter());
        registry.register(FormatType.JSON, new com.universaldiff.format.json.JsonFormatAdapter(ignoreJsonKeyOrder));
        registry.register(FormatType.XML, new com.universaldiff.format.xml.XmlFormatAdapter());
        registry.register(FormatType.BIN, new com.universaldiff.format.bin.BinaryFormatAdapter(FormatType.BIN));
        registry.register(FormatType.HEX, new com.universaldiff.format.bin.BinaryFormatAdapter(FormatType.HEX));

        FileLoader loader = new DefaultFileLoader(new DefaultFileTypeDetector(), new BomEncodingDetector());
        return new DefaultComparisonService(loader, registry);
    }
}

