package com.universaldiff.format.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.NormalizedContent;
import com.universaldiff.format.json.spi.JsonNormalizer;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Default {@link JsonNormalizer} that leverages Jackson's tree model and a deterministic flattening pass.
 * Diff adapters rely on the flattened map for stable comparisons regardless of object field order.
 */
final class JacksonTreeJsonNormalizer implements JsonNormalizer {

    private final ObjectMapper mapper;
    private final boolean ignoreKeyOrder;

    JacksonTreeJsonNormalizer(ObjectMapper mapper, boolean ignoreKeyOrder) {
        this.mapper = mapper;
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
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(path + "/" + i, node.get(i), result);
            }
            return;
        }
        if (node.isObject()) {
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
}
