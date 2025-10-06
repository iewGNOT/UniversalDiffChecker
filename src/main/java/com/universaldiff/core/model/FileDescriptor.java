package com.universaldiff.core.model;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

public final class FileDescriptor {
    private final Path path;
    private final FormatType formatType;
    private final Charset encoding;

    public FileDescriptor(Path path, FormatType formatType, Charset encoding) {
        this.path = Objects.requireNonNull(path, "path");
        this.formatType = Objects.requireNonNull(formatType, "formatType");
        this.encoding = encoding == null ? StandardCharsets.UTF_8 : encoding;
    }

    public Path getPath() {
        return path;
    }

    public FormatType getFormatType() {
        return formatType;
    }

    public Charset getEncoding() {
        return encoding;
    }
}
