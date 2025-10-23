package com.universaldiff.core.model;

import java.nio.charset.Charset;
import java.util.Optional;

public final class ComparisonOptions {
    private final FormatType forcedFormat;
    private final Charset leftEncodingOverride;
    private final Charset rightEncodingOverride;

    private ComparisonOptions(FormatType forcedFormat, Charset leftEncodingOverride, Charset rightEncodingOverride) {
        this.forcedFormat = forcedFormat;
        this.leftEncodingOverride = leftEncodingOverride;
        this.rightEncodingOverride = rightEncodingOverride;
    }

    public Optional<FormatType> forcedFormat() {
        return Optional.ofNullable(forcedFormat);
    }

    public Optional<Charset> leftEncodingOverride() {
        return Optional.ofNullable(leftEncodingOverride);
    }

    public Optional<Charset> rightEncodingOverride() {
        return Optional.ofNullable(rightEncodingOverride);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private FormatType forcedFormat;
        private Charset leftEncodingOverride;
        private Charset rightEncodingOverride;

        private Builder() {
        }

        public Builder forcedFormat(FormatType forcedFormat) {
            this.forcedFormat = forcedFormat;
            return this;
        }

        public Builder leftEncodingOverride(Charset charset) {
            this.leftEncodingOverride = charset;
            return this;
        }

        public Builder rightEncodingOverride(Charset charset) {
            this.rightEncodingOverride = charset;
            return this;
        }

        public ComparisonOptions build() {
            return new ComparisonOptions(forcedFormat, leftEncodingOverride, rightEncodingOverride);
        }
    }
}
