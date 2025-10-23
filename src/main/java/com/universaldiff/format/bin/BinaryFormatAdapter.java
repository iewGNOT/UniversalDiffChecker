package com.universaldiff.format.bin;

import com.universaldiff.core.model.DiffFragment;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.DiffSide;
import com.universaldiff.core.model.DiffType;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import com.universaldiff.format.spi.FormatAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

public class BinaryFormatAdapter implements FormatAdapter {

    private final FormatType format;

    public BinaryFormatAdapter(FormatType format) {
        if (format != FormatType.BIN && format != FormatType.HEX) {
            throw new IllegalArgumentException("BinaryFormatAdapter supports BIN or HEX only");
        }
        this.format = format;
    }

    @Override
    public NormalizedContent normalize(FileDescriptor descriptor) throws IOException {
        byte[] bytes = switch (format) {
            case BIN -> Files.readAllBytes(descriptor.getPath());
            case HEX -> parseHex(Files.readString(descriptor.getPath(), descriptor.getEncoding()));
            default -> throw new IllegalStateException("Unexpected format: " + format);
        };
        return NormalizedContent.builder(format)
                .binary(bytes)
                .encoding(descriptor.getEncoding())
                .build();
    }

    private byte[] parseHex(String content) {
        String sanitized = content.replaceAll("[^0-9a-fA-F]", "");
        int length = sanitized.length();
        if ((length & 1) == 1) {
            throw new IllegalArgumentException("Hex content must contain an even number of characters");
        }
        byte[] result = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            result[i / 2] = (byte) Integer.parseInt(sanitized.substring(i, i + 2), 16);
        }
        return result;
    }

    @Override
    public DiffResult diff(NormalizedContent left, NormalizedContent right) {
        Instant start = Instant.now();
        byte[] leftBytes = left.getBinary();
        byte[] rightBytes = right.getBinary();
        List<DiffHunk> hunks = new ArrayList<>();
        int max = Math.max(leftBytes.length, rightBytes.length);
        int index = 0;
        while (index < max) {
            byte leftValue = index < leftBytes.length ? leftBytes[index] : 0;
            byte rightValue = index < rightBytes.length ? rightBytes[index] : 0;
            boolean leftPresent = index < leftBytes.length;
            boolean rightPresent = index < rightBytes.length;
            if (!leftPresent || !rightPresent || leftValue != rightValue) {
                int startOffset = index;
                List<DiffFragment> fragments = new ArrayList<>();
                StringBuilder leftBuilder = new StringBuilder();
                StringBuilder rightBuilder = new StringBuilder();
                while (index < max) {
                    leftPresent = index < leftBytes.length;
                    rightPresent = index < rightBytes.length;
                    leftValue = leftPresent ? leftBytes[index] : 0;
                    rightValue = rightPresent ? rightBytes[index] : 0;
                    if (leftPresent && rightPresent && leftValue == rightValue) {
                        break;
                    }
                    if (leftPresent) {
                        leftBuilder.append(toHex(index, leftValue)).append(System.lineSeparator());
                    }
                    if (rightPresent) {
                        rightBuilder.append(toHex(index, rightValue)).append(System.lineSeparator());
                    }
                    index++;
                }
                int endOffset = index - 1;
                DiffType type;
                if (startOffset >= leftBytes.length) {
                    type = DiffType.INSERT;
                } else if (startOffset >= rightBytes.length) {
                    type = DiffType.DELETE;
                } else {
                    type = DiffType.MODIFY;
                }
                if (leftBuilder.length() > 0) {
                    fragments.add(new DiffFragment(
                            DiffSide.LEFT,
                            startOffset,
                            endOffset,
                            leftBuilder.toString().trim()));
                }
                if (rightBuilder.length() > 0) {
                    fragments.add(new DiffFragment(
                            DiffSide.RIGHT,
                            startOffset,
                            endOffset,
                            rightBuilder.toString().trim()));
                }
                int length = Math.max(1, endOffset - startOffset + 1);
                hunks.add(DiffHunk.of(
                        format.name().toLowerCase() + "-offset-0x" + Integer.toHexString(startOffset) + "-len-" + length,
                        type,
                        String.format("Offset 0x%08X (%d byte%s)", startOffset, length, length == 1 ? "" : "s"),
                        fragments));
            } else {
                index++;
            }
        }
        return new DiffResult(format, hunks, Duration.between(start, Instant.now()));
    }

    private String toHex(int offset, byte value) {
        try (Formatter formatter = new Formatter()) {
            formatter.format("0x%08X : 0x%02X", offset, value & 0xFF);
            return formatter.toString();
        }
    }

    @Override
    public MergeResult merge(NormalizedContent left,
                             NormalizedContent right,
                             List<MergeDecision> decisions,
                             Path outputPath) throws IOException {
        Instant start = Instant.now();
        byte[] merged = left.getBinary().clone();
        for (MergeDecision decision : decisions) {
            switch (decision.getChoice()) {
                case TAKE_LEFT -> {
                    // already left, nothing to do
                }
                case TAKE_RIGHT -> applyBinaryDecision(merged, right.getBinary(), decision.getHunkId());
                case MANUAL -> applyManualBinary(merged, decision.getHunkId(), decision.getManualContent());
            }
        }
        if (outputPath != null) {
            Files.write(outputPath, merged);
        }
        return new MergeResult(format, outputPath, Duration.between(start, Instant.now()));
    }

    private void applyBinaryDecision(byte[] target, byte[] source, String hunkId) {
        Range range = parseRange(hunkId);
        if (range == null) {
            return;
        }
        for (int i = 0; i < range.length; i++) {
            int offset = range.offset + i;
            if (offset < target.length && offset < source.length) {
                target[offset] = source[offset];
            }
        }
    }

    private void applyManualBinary(byte[] target, String hunkId, String manualContent) {
        if (manualContent == null || manualContent.isBlank()) {
            return;
        }
        Range range = parseRange(hunkId);
        if (range == null) {
            return;
        }
        byte[] manualBytes = parseHex(manualContent);
        for (int i = 0; i < manualBytes.length && i < range.length; i++) {
            int offset = range.offset + i;
            if (offset < target.length) {
                target[offset] = manualBytes[i];
            }
        }
    }

    private Range parseRange(String hunkId) {
        if (hunkId == null) {
            return null;
        }
        String[] segments = hunkId.split("-len-");
        if (segments.length != 2) {
            return null;
        }
        String offsetHex = segments[0].replaceAll("[^0-9a-fA-F]", "");
        try {
            int offset = Integer.parseInt(offsetHex, 16);
            int length = Integer.parseInt(segments[1]);
            return new Range(offset, length);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record Range(int offset, int length) {}
}

