package com.universaldiff.format.json;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import com.universaldiff.format.json.spi.JsonMerger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Applies merge decisions by navigating JSON pointers encoded in diff hunk identifiers.
 * Delegates parsing and serialization to Jackson to honour JSON semantics.
 */
final class PointerAwareJsonMerger implements JsonMerger {

    private final ObjectMapper mapper;

    PointerAwareJsonMerger(ObjectMapper mapper) {
        this.mapper = mapper;
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
                    // already left, nothing to do
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
        Path writtenPath = outputPath;
        if (outputPath != null) {
            String serialized = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(merged);
            Files.writeString(outputPath, serialized, left.getEncoding());
        }
        return new MergeResult(FormatType.JSON, writtenPath, Duration.between(start, Instant.now()));
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
            } else {
                throw new IllegalArgumentException("Pointer path traversed a non-container node: " + pointer);
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
        } else {
            throw new IllegalArgumentException("Pointer path terminated in a non-container node: " + pointer);
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

    private String decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private String unescape(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }
}
