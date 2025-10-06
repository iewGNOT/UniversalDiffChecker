package com.universaldiff.core.detect;

import com.universaldiff.core.model.FormatType;

import java.nio.file.Path;

public interface FileTypeDetector {
    DetectionResult detect(Path path);

    final class DetectionResult {
        private final FormatType formatType;
        private final boolean confident;

        public DetectionResult(FormatType formatType, boolean confident) {
            this.formatType = formatType;
            this.confident = confident;
        }

        public FormatType getFormatType() {
            return formatType;
        }

        public boolean isConfident() {
            return confident;
        }
    }
}
