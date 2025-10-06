package com.universaldiff.core.model;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Canonical, normalized representation of content ready for diffing/merging.
 */
public final class NormalizedContent {
    private final FormatType formatType;
    private final List<String> logicalRecords;
    private final byte[] binary;
    private final Object nativeModel;
    private final Charset encoding;

    private NormalizedContent(FormatType formatType,
                              List<String> logicalRecords,
                              byte[] binary,
                              Object nativeModel,
                              Charset encoding) {
        this.formatType = Objects.requireNonNull(formatType, "formatType");
        this.logicalRecords = logicalRecords == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(logicalRecords));
        this.binary = binary;
        this.nativeModel = nativeModel;
        this.encoding = encoding == null ? StandardCharsets.UTF_8 : encoding;
    }

    public FormatType getFormatType() {
        return formatType;
    }

    public List<String> getLogicalRecords() {
        return logicalRecords;
    }

    public byte[] getBinary() {
        return binary;
    }

    public Object getNativeModel() {
        return nativeModel;
    }

    public Charset getEncoding() {
        return encoding;
    }

    public static Builder builder(FormatType formatType) {
        return new Builder(formatType);
    }

    public static final class Builder {
        private final FormatType formatType;
        private List<String> logicalRecords;
        private byte[] binary;
        private Object nativeModel;
        private Charset encoding;

        private Builder(FormatType formatType) {
            this.formatType = Objects.requireNonNull(formatType, "formatType");
        }

        public Builder logicalRecords(List<String> logicalRecords) {
            this.logicalRecords = logicalRecords;
            return this;
        }

        public Builder binary(byte[] binary) {
            this.binary = binary;
            return this;
        }

        public Builder nativeModel(Object nativeModel) {
            this.nativeModel = nativeModel;
            return this;
        }

        public Builder encoding(Charset encoding) {
            this.encoding = encoding;
            return this;
        }

        public NormalizedContent build() {
            return new NormalizedContent(formatType, logicalRecords, binary, nativeModel, encoding);
        }
    }
}
