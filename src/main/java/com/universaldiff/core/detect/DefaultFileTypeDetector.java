package com.universaldiff.core.detect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universaldiff.core.model.FormatType;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Best-effort detector that combines extension and lightweight content sniffing.
 */
public class DefaultFileTypeDetector implements FileTypeDetector {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DetectionResult detect(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return new DetectionResult(FormatType.UNKNOWN, false);
        }

        final String filename = path.getFileName().toString();
        final int dotIndex = filename.lastIndexOf('.') + 1;
        FormatType byExtension = FormatType.UNKNOWN;
        if (dotIndex > 0 && dotIndex < filename.length()) {
            String extension = filename.substring(dotIndex).toLowerCase(Locale.ENGLISH);
            byExtension = FormatType.fromExtension(extension);
            if (byExtension != FormatType.UNKNOWN) {
                return new DetectionResult(byExtension, true);
            }
        }

        try (InputStream in = Files.newInputStream(path)) {
            byte[] head = in.readNBytes(4096);
            if (looksLikeJson(head)) {
                return new DetectionResult(FormatType.JSON, true);
            }
            if (looksLikeXml(head)) {
                return new DetectionResult(FormatType.XML, true);
            }
            if (looksLikeCsv(head)) {
                return new DetectionResult(FormatType.CSV, false);
            }
            if (containsNonPrintable(head)) {
                return new DetectionResult(FormatType.BIN, false);
            }
            return new DetectionResult(FormatType.TXT, false);
        } catch (IOException e) {
            return new DetectionResult(FormatType.UNKNOWN, false);
        }
    }

    private boolean looksLikeJson(byte[] sample) {
        try {
            JsonNode node = objectMapper.readTree(sample);
            return node != null && (node.isObject() || node.isArray());
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean looksLikeXml(byte[] sample) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            Document document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(sample));
            return document != null && document.getDocumentElement() != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean looksLikeCsv(byte[] sample) {
        String content = new String(sample);
        int commas = content.length() - content.replace(",", "").length();
        int newLines = content.length() - content.replace("\n", "").length();
        return commas > 0 && newLines > 0 && commas >= newLines;
    }

    private boolean containsNonPrintable(byte[] bytes) {
        for (byte b : bytes) {
            int value = b & 0xFF;
            if (value < 0x09 && value != '\n' && value != '\r' && value != '\t') {
                return true;
            }
            if (value > 0x7E) {
                return true;
            }
        }
        return false;
    }
}
