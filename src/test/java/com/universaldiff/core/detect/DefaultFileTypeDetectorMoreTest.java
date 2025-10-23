package com.universaldiff.core.detect;

import com.universaldiff.core.io.BomEncodingDetector;
import com.universaldiff.core.io.EncodingDetector;
import com.universaldiff.core.model.FormatType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFileTypeDetectorMoreTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsBinaryWhenLeadingBytesAreNonPrintable() throws Exception {
        Path binFile = tempDir.resolve("data.data");
        Files.write(binFile, new byte[]{0x00, 0x01, 0x02, 0x03});

        DefaultFileTypeDetector detector = new DefaultFileTypeDetector();
        FileTypeDetector.DetectionResult result = detector.detect(binFile);

        assertThat(result.getFormatType()).isEqualTo(FormatType.BIN);
        assertThat(result.isConfident()).isFalse();
    }

    @Test
    void extensionTakesPrecedenceOverContent() throws Exception {
        Path jsonFile = Files.writeString(tempDir.resolve("data.json"), "not actually json", StandardCharsets.UTF_8);

        DefaultFileTypeDetector detector = new DefaultFileTypeDetector();
        FileTypeDetector.DetectionResult result = detector.detect(jsonFile);

        assertThat(result.getFormatType()).isEqualTo(FormatType.JSON);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void csvHeuristicDoesNotTriggerForSingleLine() throws Exception {
        Path singleLine = Files.writeString(tempDir.resolve("single.csv"), "1,2,3", StandardCharsets.UTF_8);

        DefaultFileTypeDetector detector = new DefaultFileTypeDetector();
        FileTypeDetector.DetectionResult result = detector.detect(singleLine);

        assertThat(result.getFormatType()).isEqualTo(FormatType.CSV);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void bomDetectorRecognizesUtf16Be() throws Exception {
        Path utf16 = tempDir.resolve("utf16.txt");
        Files.write(utf16, new byte[]{(byte) 0xFE, (byte) 0xFF, 0x00, 'A'});

        EncodingDetector detector = new BomEncodingDetector();
        assertThat(detector.detect(utf16)).isEqualTo(StandardCharsets.UTF_16BE);
    }

    @Test
    void bomDetectorDefaultsToUtf8() throws Exception {
        EncodingDetector detector = new BomEncodingDetector();
        Path shortFile = Files.write(tempDir.resolve("short.txt"), new byte[]{0x41});
        Path directory = Files.createDirectory(tempDir.resolve("dir"));

        assertThat(detector.detect(shortFile)).isEqualTo(StandardCharsets.UTF_8);
        assertThat(detector.detect(directory)).isEqualTo(StandardCharsets.UTF_8);
        assertThat(detector.detect(null)).isEqualTo(StandardCharsets.UTF_8);
    }
}
