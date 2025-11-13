package com.universaldiff.core.io;

import com.universaldiff.core.detect.FileTypeDetector;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultFileLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void detectReturnsDescriptorFromInjectedDetectors() throws Exception {
        Path file = Files.writeString(tempDir.resolve("data.bin"), "payload", StandardCharsets.UTF_8);
        FileTypeDetector detector = path -> new FileTypeDetector.DetectionResult(FormatType.JSON, true);
        EncodingDetector encodingDetector = path -> StandardCharsets.ISO_8859_1;

        DefaultFileLoader loader = new DefaultFileLoader(detector, encodingDetector);
        FileDescriptor descriptor = loader.detect(file);

        assertThat(descriptor.getPath()).isEqualTo(file);
        assertThat(descriptor.getFormatType()).isEqualTo(FormatType.JSON);
        assertThat(descriptor.getEncoding()).isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    void detectRejectsMissingFiles() {
        FileTypeDetector detector = path -> new FileTypeDetector.DetectionResult(FormatType.UNKNOWN, false);
        EncodingDetector encodingDetector = path -> StandardCharsets.UTF_8;
        DefaultFileLoader loader = new DefaultFileLoader(detector, encodingDetector);

        Path missing = tempDir.resolve("missing.dat");
        assertThatThrownBy(() -> loader.detect(missing))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void detectRejectsNullPaths() {
        FileTypeDetector detector = path -> new FileTypeDetector.DetectionResult(FormatType.UNKNOWN, false);
        EncodingDetector encodingDetector = path -> StandardCharsets.UTF_8;

        DefaultFileLoader loader = new DefaultFileLoader(detector, encodingDetector);

        assertThatThrownBy(() -> loader.detect(null))
                .isInstanceOf(IOException.class);
    }
}
