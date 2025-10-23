package com.universaldiff.core.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects UTF variants via BOM and defaults to UTF-8.
 */
public final class BomEncodingDetector implements EncodingDetector {

    private static final Logger log = LoggerFactory.getLogger(BomEncodingDetector.class);

    @Override
    public Charset detect(Path path) {
        if (path == null) {
            return StandardCharsets.UTF_8;
        }
        try (InputStream in = Files.newInputStream(path)) {
            byte[] bom = in.readNBytes(3);
            if (bom.length >= 2) {
                if (bom[0] == (byte) 0xFE && bom[1] == (byte) 0xFF) {
                    return StandardCharsets.UTF_16BE;
                }
                if (bom[0] == (byte) 0xFF && bom[1] == (byte) 0xFE) {
                    return StandardCharsets.UTF_16LE;
                }
            }
            if (bom.length == 3 && bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF) {
                return StandardCharsets.UTF_8;
            }
        } catch (IOException ex) {
            log.debug("Unable to read BOM for {}: {}", path, ex.getMessage());
        }
        return StandardCharsets.UTF_8;
    }
}
