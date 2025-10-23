package com.universaldiff.format.csv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CsvTable {
    private final List<String> headers;
    private final List<List<String>> rows;

    public CsvTable(List<String> headers, List<List<String>> rows) {
        this.headers = headers == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(headers));
        this.rows = rows == null ? List.of() : Collections.unmodifiableList(rows.stream()
                .map(row -> Collections.unmodifiableList(new ArrayList<>(row)))
                .toList());
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public String formatRow(int index) {
        if (index < 0 || index >= rows.size()) {
            return "";
        }
        List<String> row = rows.get(index);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            String header = headers.isEmpty() ? "col" + (i + 1) : headers.get(i);
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(header).append("=").append(row.get(i));
        }
        return builder.toString();
    }

    public int size() {
        return rows.size();
    }
}

