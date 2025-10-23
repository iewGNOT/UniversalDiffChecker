package com.universaldiff.core.io;

import com.universaldiff.core.detect.FileTypeDetector;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class DefaultFileLoader implements FileLoader {

    private final FileTypeDetector detector;
    private final EncodingDetector encodingDetector;

    public DefaultFileLoader(FileTypeDetector detector, EncodingDetector encodingDetector) {
        this.detector = Objects.requireNonNull(detector, "detector");
        this.encodingDetector = Objects.requireNonNull(encodingDetector, "encodingDetector");
    }

    @Override
    public FileDescriptor detect(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            throw new IOException("File does not exist: " + path);
        }
        FileTypeDetector.DetectionResult detection = detector.detect(path);
        FormatType format = detection.getFormatType();
        Charset encoding = encodingDetector.detect(path);
        return new FileDescriptor(path, format, encoding);
    }
}
