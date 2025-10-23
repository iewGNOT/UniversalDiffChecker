package com.universaldiff.format.spi;

import com.universaldiff.core.model.FormatType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class InMemoryFormatAdapterRegistry implements FormatAdapterRegistry {

    private final Map<FormatType, FormatAdapter> adapters = new EnumMap<>(FormatType.class);

    @Override
    public void register(FormatType type, FormatAdapter adapter) {
        adapters.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(adapter, "adapter"));
    }

    @Override
    public FormatAdapter getAdapter(FormatType type) {
        FormatAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter registered for format: " + type);
        }
        return adapter;
    }
}
