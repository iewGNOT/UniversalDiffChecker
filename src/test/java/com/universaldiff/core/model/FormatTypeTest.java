package com.universaldiff.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormatTypeTest {

    @Test
    void fromExtensionReturnsUnknownForNullOrBlank() {
        assertThat(FormatType.fromExtension(null)).isEqualTo(FormatType.UNKNOWN);
        assertThat(FormatType.fromExtension("   ")).isEqualTo(FormatType.UNKNOWN);
    }

    @Test
    void fromExtensionIsCaseInsensitive() {
        assertThat(FormatType.fromExtension("TXT")).isEqualTo(FormatType.TXT);
    }
}
