package com.universaldiff.core.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BomEncodingDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsUtf16LeWithBom() throws Exception {
        Path file = tempDir.resolve("utf16.txt");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xFE, 'a', 0});

        EncodingDetector detector = new BomEncodingDetector();
        Charset charset = detector.detect(file);

        assertThat(charset).isEqualTo(StandardCharsets.UTF_16LE);
    }

    @Test
    void defaultsToUtf8WithoutBom() throws Exception {
        Path file = Files.writeString(tempDir.resolve("plain.txt"), "hello", StandardCharsets.UTF_8);

        EncodingDetector detector = new BomEncodingDetector();
        Charset charset = detector.detect(file);

        assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
    }
}
