package com.universaldiff.format.json;

import com.universaldiff.core.model.DiffFragment;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.DiffSide;
import com.universaldiff.core.model.DiffType;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.NormalizedContent;
import com.universaldiff.format.json.spi.JsonDiffer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes JSON diffs by comparing flattened path-value maps.
 * Encodes JSON pointer paths in a stable manner to produce hunk identifiers that downstream merges can interpret.
 */
final class PathAwareJsonDiffer implements JsonDiffer {

    @Override
    public DiffResult diff(NormalizedContent left, NormalizedContent right) {
        Instant start = Instant.now();
        Map<String, String> leftMap = toMap(left);
        Map<String, String> rightMap = toMap(right);
        Map<String, DiffType> diffIndex = new LinkedHashMap<>();
        Map<String, List<DiffFragment>> fragments = new LinkedHashMap<>();

        for (String path : leftMap.keySet()) {
            if (!rightMap.containsKey(path)) {
                diffIndex.put(path, DiffType.DELETE);
                fragments.put(path, List.of(new DiffFragment(DiffSide.LEFT, 0, 0, leftMap.get(path))));
            } else if (!leftMap.get(path).equals(rightMap.get(path))) {
                diffIndex.put(path, DiffType.MODIFY);
                fragments.put(path, List.of(
                        new DiffFragment(DiffSide.LEFT, 0, 0, leftMap.get(path)),
                        new DiffFragment(DiffSide.RIGHT, 0, 0, rightMap.get(path))
                ));
            }
        }
        for (String path : rightMap.keySet()) {
            if (!leftMap.containsKey(path)) {
                diffIndex.put(path, DiffType.INSERT);
                fragments.put(path, List.of(new DiffFragment(DiffSide.RIGHT, 0, 0, rightMap.get(path))));
            }
        }

        List<DiffHunk> hunks = new ArrayList<>();
        for (Map.Entry<String, DiffType> entry : diffIndex.entrySet()) {
            String path = entry.getKey();
            DiffType type = entry.getValue();
            List<DiffFragment> fragList = fragments.get(path);
            hunks.add(DiffHunk.of(
                    "json-path-" + encode(path),
                    type,
                    path.isEmpty() ? "/" : path,
                    fragList));
        }

        return new DiffResult(FormatType.JSON, hunks, Duration.between(start, Instant.now()));
    }

    private Map<String, String> toMap(NormalizedContent content) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String record : content.getLogicalRecords()) {
            int sep = record.indexOf(" = ");
            if (sep > 0) {
                String path = record.substring(0, sep);
                String value = record.substring(sep + 3);
                map.put(path, value);
            }
        }
        return map;
    }

    private String encode(String path) {
        return Base64.getEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8));
    }
}
