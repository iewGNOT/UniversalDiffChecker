package com.universaldiff.core.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonOptionsTest {

    @Test
    void builderPopulatesOptionals() {
        ComparisonOptions options = ComparisonOptions.builder()
                .forcedFormat(FormatType.JSON)
                .leftEncodingOverride(StandardCharsets.UTF_16)
                .rightEncodingOverride(StandardCharsets.ISO_8859_1)
                .build();

        assertThat(options.forcedFormat()).contains(FormatType.JSON);
        assertThat(options.leftEncodingOverride()).contains(StandardCharsets.UTF_16);
        assertThat(options.rightEncodingOverride()).contains(StandardCharsets.ISO_8859_1);
    }

    @Test
    void builderDefaultsOptionalValuesToEmpty() {
        ComparisonOptions options = ComparisonOptions.builder().build();

        assertThat(options.forcedFormat()).isEmpty();
        assertThat(options.leftEncodingOverride()).isEmpty();
        assertThat(options.rightEncodingOverride()).isEmpty();
    }
}
