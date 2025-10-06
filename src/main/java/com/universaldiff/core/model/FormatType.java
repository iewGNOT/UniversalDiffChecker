package com.universaldiff.core.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Supported file formats for the Universal Difference Checker.
 */
public enum FormatType {
    TXT("txt", "log", "cfg", "ini"),
    BIN("bin"),
    HEX("hex"),
    CSV("csv"),
    JSON("json"),
    XML("xml"),
    UNKNOWN();

    private final Set<String> extensions;

    FormatType(String... extensions) {
        if (extensions == null || extensions.length == 0) {
            this.extensions = Collections.emptySet();
        } else {
            this.extensions = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(extensions)));
        }
    }

    public Set<String> getExtensions() {
        return extensions;
    }

    public static FormatType fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return UNKNOWN;
        }
        final String normalized = extension.toLowerCase();
        for (FormatType type : values()) {
            if (type.getExtensions().contains(normalized)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
