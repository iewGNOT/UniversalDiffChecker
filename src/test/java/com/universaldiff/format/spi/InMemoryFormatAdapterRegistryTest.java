package com.universaldiff.format.spi;

import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.NormalizedContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryFormatAdapterRegistryTest {

    @Test
    void getAdapterThrowsForUnregisteredType() {
        InMemoryFormatAdapterRegistry registry = new InMemoryFormatAdapterRegistry();

        assertThatThrownBy(() -> registry.getAdapter(FormatType.JSON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void normalizedContentIsImmutableAndDefaultsEncoding() {
        NormalizedContent content = NormalizedContent.builder(FormatType.TXT)
                .logicalRecords(List.of("line"))
                .encoding(null)
                .build();

        assertThat(content.getEncoding()).isEqualTo(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content.getLogicalRecords()).containsExactly("line");
        assertThatThrownBy(() -> content.getLogicalRecords().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
