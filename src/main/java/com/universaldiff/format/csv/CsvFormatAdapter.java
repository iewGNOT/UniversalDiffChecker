package com.universaldiff.format.csv;

import com.universaldiff.core.model.DiffFragment;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.DiffSide;
import com.universaldiff.core.model.DiffType;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import com.universaldiff.format.spi.FormatAdapter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CsvFormatAdapter implements FormatAdapter {

    @Override
    public NormalizedContent normalize(FileDescriptor descriptor) throws IOException {
        CsvTable table = parseTable(descriptor.getPath(), descriptor.getEncoding());
        List<String> logical = new ArrayList<>();
        for (int i = 0; i < table.size(); i++) {
            logical.add(table.formatRow(i));
        }
        return NormalizedContent.builder(FormatType.CSV)
                .logicalRecords(logical)
                .nativeModel(table)
                .encoding(descriptor.getEncoding())
                .build();
    }

    private CsvTable parseTable(Path path, java.nio.charset.Charset encoding) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setTrim(true)
                .setSkipHeaderRecord(false)
                .setIgnoreSurroundingSpaces(true)
                .build();
        try (Reader reader = Files.newBufferedReader(path, encoding);
             CSVParser parser = new CSVParser(reader, format)) {
            List<CSVRecord> records = parser.getRecords();
            List<String> headers = new ArrayList<>();
            int startIndex = 0;
            if (!records.isEmpty()) {
                CSVRecord first = records.get(0);
                if (looksLikeHeader(first)) {
                    headers.addAll(first.stream().map(String::trim).toList());
                    startIndex = 1;
                }
            }
            if (headers.isEmpty() && !records.isEmpty()) {
                for (int i = 0; i < records.get(0).size(); i++) {
                    headers.add("col" + (i + 1));
                }
            }
            List<List<String>> rows = new ArrayList<>();
            for (int i = startIndex; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                List<String> row = new ArrayList<>();
                for (int c = 0; c < headers.size(); c++) {
                    row.add(record.size() > c ? record.get(c).trim() : "");
                }
                rows.add(row);
            }
            return new CsvTable(headers, rows);
        }
    }

    private boolean looksLikeHeader(CSVRecord record) {
        for (String value : record) {
            if (value == null || value.isBlank()) {
                return false;
            }
            if (value.chars().anyMatch(Character::isDigit)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DiffResult diff(NormalizedContent left, NormalizedContent right) {
        Instant start = Instant.now();
        CsvTable leftTable = (CsvTable) left.getNativeModel();
        CsvTable rightTable = (CsvTable) right.getNativeModel();
        int keyIndex = determineKeyIndex(leftTable, rightTable);

        Map<String, List<String>> leftMap = indexTable(leftTable, keyIndex);
        Map<String, List<String>> rightMap = indexTable(rightTable, keyIndex);
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(leftMap.keySet());
        keys.addAll(rightMap.keySet());

        List<DiffHunk> hunks = new ArrayList<>();
        for (String key : keys) {
            List<String> leftRow = leftMap.get(key);
            List<String> rightRow = rightMap.get(key);
            if (leftRow != null && rightRow != null) {
                if (!leftRow.equals(rightRow)) {
                    hunks.add(DiffHunk.of(
                            "csv-row-" + encodeKey(key),
                            DiffType.MODIFY,
                            "Row " + key,
                            List.of(
                                    new DiffFragment(DiffSide.LEFT, 0, leftRow.size() - 1, renderRow(leftTable, leftRow)),
                                    new DiffFragment(DiffSide.RIGHT, 0, rightRow.size() - 1, renderRow(rightTable, rightRow))
                            )));
                }
            } else if (leftRow == null) {
                hunks.add(DiffHunk.of(
                        "csv-row-" + encodeKey(key),
                        DiffType.INSERT,
                        "Insert row " + key,
                        List.of(new DiffFragment(DiffSide.RIGHT, 0, 0, renderRow(rightTable, rightRow)))));
            } else {
                hunks.add(DiffHunk.of(
                        "csv-row-" + encodeKey(key),
                        DiffType.DELETE,
                        "Delete row " + key,
                        List.of(new DiffFragment(DiffSide.LEFT, 0, 0, renderRow(leftTable, leftRow)))));
            }
        }
        return new DiffResult(FormatType.CSV, hunks, Duration.between(start, Instant.now()));
    }

    private String encodeKey(String key) {
        if (key == null) {
            return Base64.getEncoder().encodeToString("<null>".getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        return "<null>".equals(decoded) ? "" : decoded;
    }

    private int determineKeyIndex(CsvTable left, CsvTable right) {
        List<String> headers = !left.getHeaders().isEmpty() ? left.getHeaders() : right.getHeaders();
        if (headers.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i).toLowerCase(Locale.ENGLISH);
            if (header.contains("id") || header.contains("key") || header.contains("name")) {
                return i;
            }
        }
        return 0;
    }

    private Map<String, List<String>> indexTable(CsvTable table, int keyIndex) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < table.getRows().size(); i++) {
            List<String> row = table.getRows().get(i);
            String key = keyIndex < row.size() ? row.get(keyIndex) : "row-" + i;
            map.put(key, row);
        }
        return map;
    }

    private String renderRow(CsvTable table, List<String> row) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            String header = table.getHeaders().isEmpty() ? "col" + (i + 1) : table.getHeaders().get(i);
            builder.append(header).append("=").append(row.get(i));
        }
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public MergeResult merge(NormalizedContent left,
                             NormalizedContent right,
                             List<MergeDecision> decisions,
                             Path outputPath) throws IOException {
        Instant start = Instant.now();
        CsvTable leftTable = (CsvTable) left.getNativeModel();
        CsvTable rightTable = (CsvTable) right.getNativeModel();
        int keyIndex = determineKeyIndex(leftTable, rightTable);
        Map<String, List<String>> merged = new LinkedHashMap<>(indexTable(leftTable, keyIndex));
        Map<String, List<String>> rightMap = indexTable(rightTable, keyIndex);
        for (MergeDecision decision : decisions) {
            String key = decodeKey(decision.getHunkId().replace("csv-row-", ""));
            switch (decision.getChoice()) {
                case TAKE_LEFT -> {
                    // nothing: keep left row
                }
                case TAKE_RIGHT -> {
                    List<String> rightRow = rightMap.get(key);
                    if (rightRow != null) {
                        merged.put(key, new ArrayList<>(rightRow));
                    }
                }
                case MANUAL -> {
                    if (decision.getManualContent() != null) {
                        merged.put(key, parseManualRow(decision.getManualContent(), leftTable.getHeaders()));
                    }
                }
            }
        }
        if (outputPath != null) {
            List<String> lines = new ArrayList<>();
            if (!leftTable.getHeaders().isEmpty()) {
                lines.add(String.join(",", leftTable.getHeaders()));
            }
            for (List<String> row : merged.values()) {
                lines.add(formatCsvLine(row));
            }
            Files.write(outputPath, lines, left.getEncoding());
        }
        return new MergeResult(FormatType.CSV, outputPath, Duration.between(start, Instant.now()));
    }

    private List<String> parseManualRow(String manualContent, List<String> headers) {
        String[] parts = manualContent.split("\\|");
        List<String> row = new ArrayList<>();
        for (String part : parts) {
            part = part.trim();
            String[] kv = part.split("=", 2);
            String value = kv.length == 2 ? kv[1].trim() : kv[0].trim();
            row.add(value);
        }
        while (row.size() < headers.size()) {
            row.add("");
        }
        return row;
    }

    private String formatCsvLine(List<String> row) {
        List<String> escaped = new ArrayList<>();
        for (String value : row) {
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                escaped.add('"' + value.replace("\"", "\"\"") + '"');
            } else {
                escaped.add(value);
            }
        }
        return String.join(",", escaped);
    }
}














