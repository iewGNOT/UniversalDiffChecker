package com.universaldiff.format.csv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTableTest {

    @Test
    void formatRowReturnsEmptyWhenIndexOutOfBounds() {
        CsvTable table = new CsvTable(List.of("a", "b"), List.of(List.of("1", "2")));
        assertThat(table.formatRow(-1)).isEmpty();
        assertThat(table.formatRow(5)).isEmpty();
    }

    @Test
    void formatRowUsesGeneratedHeadersWhenMissing() {
        CsvTable table = new CsvTable(List.of(), List.of(List.of("x", "y")));
        assertThat(table.formatRow(0)).isEqualTo("col1=x | col2=y");
    }
}
