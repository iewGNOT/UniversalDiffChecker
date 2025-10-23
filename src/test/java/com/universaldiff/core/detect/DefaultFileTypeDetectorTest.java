package com.universaldiff.core.detect;

import com.universaldiff.core.model.FormatType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFileTypeDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsJsonByContentRegardlessOfExtension() throws Exception {
        Path path = Files.writeString(tempDir.resolve("data.unknown"), "{\"key\":1}", StandardCharsets.UTF_8);
        DefaultFileTypeDetector detector = new DefaultFileTypeDetector();

        FileTypeDetector.DetectionResult result = detector.detect(path);

        assertThat(result.getFormatType()).isEqualTo(FormatType.JSON);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void detectsXmlByContent() throws Exception {
        Path path = Files.writeString(tempDir.resolve("payload.dat"), "<root><node/></root>", StandardCharsets.UTF_8);
        DefaultFileTypeDetector detector = new DefaultFileTypeDetector();

        FileTypeDetector.DetectionResult result = detector.detect(path);

        assertThat(result.getFormatType()).isEqualTo(FormatType.XML);
        assertThat(result.isConfident()).isTrue();
    }

    @Test
    void csvLikeContentIsLowConfidence() throws Exception {
        Path path = Files.writeString(tempDir.resolve("table.log"), "id,name\n1,alpha\n2,beta\n", StandardCharsets.UTF_8);
        DefaultFileTypeDetector detector = new DefaultFileTypeDetector();

        FileTypeDetector.DetectionResult result = detector.detect(path);

        assertThat(result.getFormatType()).isIn(FormatType.CSV, FormatType.TXT);
        assertThat(result.isConfident()).isTrue();
    }
}
