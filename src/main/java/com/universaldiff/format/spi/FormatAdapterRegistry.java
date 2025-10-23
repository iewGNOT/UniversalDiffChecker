package com.universaldiff.format.spi;

import com.universaldiff.core.model.FormatType;

public interface FormatAdapterRegistry {

    void register(FormatType type, FormatAdapter adapter);

    FormatAdapter getAdapter(FormatType type);
}
