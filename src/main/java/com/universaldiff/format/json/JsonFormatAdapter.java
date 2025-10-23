package com.universaldiff.format.json;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.universaldiff.core.model.DiffFragment;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.DiffSide;
import com.universaldiff.core.model.DiffType;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import com.universaldiff.format.spi.FormatAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class JsonFormatAdapter implements FormatAdapter {

    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean ignoreKeyOrder;

    public JsonFormatAdapter() {
        this(true);
    }

    public JsonFormatAdapter(boolean ignoreKeyOrder) {
        this.ignoreKeyOrder = ignoreKeyOrder;
    }

    @Override
    public NormalizedContent normalize(FileDescriptor descriptor) throws IOException {
        String raw = Files.readString(descriptor.getPath(), descriptor.getEncoding());
        JsonNode root = mapper.readTree(raw);
        Map<String, String> flattened = new TreeMap<>();
        flatten("", root, flattened);
        List<String> logical = flattened.entrySet().stream()
                .map(e -> e.getKey() + " = " + e.getValue())
                .toList();
        return NormalizedContent.builder(FormatType.JSON)
                .logicalRecords(logical)
                .nativeModel(root)
                .encoding(descriptor.getEncoding())
                .build();
    }

    private void flatten(String path, JsonNode node, Map<String, String> result) {
        if (node == null) {
            result.put(path, "null");
            return;
        }
        if (node.isValueNode()) {
            result.put(path, mapper.convertValue(node, String.class));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(path + "/" + i, node.get(i), result);
            }
        } else if (node.isObject()) {
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            if (ignoreKeyOrder) {
                fieldNames.sort(String::compareTo);
            }
            for (String field : fieldNames) {
                flatten(path + "/" + escape(field), node.get(field), result);
            }
        }
    }

    private String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    private String unescape(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

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
                    "JSON Pointer " + path,
                    fragList));
        }
        return new DiffResult(FormatType.JSON, hunks, Duration.between(start, Instant.now()));
    }

    private String encode(String path) {
        return Base64.getEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
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

    @Override
    public MergeResult merge(NormalizedContent left,
                             NormalizedContent right,
                             List<MergeDecision> decisions,
                             Path outputPath) throws IOException {
        Instant start = Instant.now();
        JsonNode leftRoot = (JsonNode) left.getNativeModel();
        if (!(leftRoot instanceof ObjectNode objectRoot)) {
            throw new IOException("JSON merge currently supports object roots only");
        }
        ObjectNode merged = objectRoot.deepCopy();
        JsonNode rightRoot = (JsonNode) right.getNativeModel();
        for (MergeDecision decision : decisions) {
            String pointerEncoded = decision.getHunkId().replace("json-path-", "");
            String pointer = decode(pointerEncoded);
            switch (decision.getChoice()) {
                case TAKE_LEFT -> {
                    // nothing to do
                }
                case TAKE_RIGHT -> {
                    JsonNode rightValue = resolveNode(rightRoot, pointer);
                    setValue(merged, pointer, rightValue);
                }
                case MANUAL -> {
                    if (decision.getManualContent() != null) {
                        JsonNode manual = mapper.readTree(decision.getManualContent());
                        setValue(merged, pointer, manual);
                    }
                }
            }
        }
        if (outputPath != null) {
            Files.writeString(outputPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(merged), left.getEncoding());
        }
        return new MergeResult(FormatType.JSON, outputPath, Duration.between(start, Instant.now()));
    }

    private JsonNode resolveNode(JsonNode root, String pointer) {
        JsonPointer jsonPointer = JsonPointer.compile(pointer);
        return root.at(jsonPointer);
    }

    private void setValue(ObjectNode root, String pointer, JsonNode value) {
        if ("".equals(pointer)) {
            throw new IllegalArgumentException("Cannot replace root node");
        }
        String[] tokens = pointer.substring(1).split("/");
        JsonNode current = root;
        for (int i = 0; i < tokens.length - 1; i++) {
            String token = unescape(tokens[i]);
            if (current.isObject()) {
                ObjectNode objectNode = (ObjectNode) current;
                JsonNode child = objectNode.get(token);
                if (child == null || child.isNull()) {
                    child = guessChild(tokens[i + 1]);
                    objectNode.set(token, child);
                }
                current = child;
            } else if (current.isArray()) {
                int index = Integer.parseInt(token);
                ArrayNode arrayNode = (ArrayNode) current;
                ensureSize(arrayNode, index + 1);
                JsonNode child = arrayNode.get(index);
                if (child == null || child.isNull()) {
                    child = guessChild(tokens[i + 1]);
                    arrayNode.set(index, child);
                }
                current = child;
            }
        }
        String lastToken = unescape(tokens[tokens.length - 1]);
        if (current.isObject()) {
            ((ObjectNode) current).set(lastToken, value);
        } else if (current.isArray()) {
            ArrayNode arrayNode = (ArrayNode) current;
            int index = Integer.parseInt(lastToken);
            ensureSize(arrayNode, index + 1);
            arrayNode.set(index, value);
        }
    }

    private JsonNode guessChild(String token) {
        if (token.matches("\\d+")) {
            return JsonNodeFactory.instance.arrayNode();
        }
        return JsonNodeFactory.instance.objectNode();
    }

    private void ensureSize(ArrayNode arrayNode, int size) {
        while (arrayNode.size() < size) {
            arrayNode.add(JsonNodeFactory.instance.nullNode());
        }
    }
}









