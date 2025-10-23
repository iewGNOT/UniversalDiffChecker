package com.universaldiff.format.xml;

import java.io.IOException;

/**
 * Signals failures while parsing, diffing, or merging XML content.
 */
public final class XmlProcessingException extends IOException {

    public XmlProcessingException(String message) {
        super(message);
    }

    public XmlProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
