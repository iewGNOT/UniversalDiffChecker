package com.universaldiff.format.xml;

import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.DiffType;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class XmlAttributesTest {

    @TempDir
    Path tempDir;

    private String previousFactoryProperty;

    @AfterEach
    void restoreFactoryProperty() {
        if (previousFactoryProperty != null) {
            System.setProperty(DocumentBuilderFactory.class.getName(), previousFactoryProperty);
        } else {
            System.clearProperty(DocumentBuilderFactory.class.getName());
        }
        previousFactoryProperty = null;
    }

    @Test
    void diff_andMerge_handleAttributeChanges() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.xml"),
                "<root><node id=\"1\" status=\"old\">value</node><node id=\"2\" flag=\"no\">other</node></root>",
                StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.xml"),
                "<root><node id=\"1\" status=\"new\">value</node><node id=\"2\" flag=\"yes\">other</node></root>",
                StandardCharsets.UTF_8);
        Path output = tempDir.resolve("merged.xml");

        XmlFormatAdapter adapter = new XmlFormatAdapter();
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.XML, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.XML, StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        assertThat(diff.getHunks())
                .extracting(h -> decodePointer(h.getId()), DiffHunk::getType)
                .containsExactlyInAnyOrder(
                        tuple("/root[1]/node[1]/@status", DiffType.MODIFY),
                        tuple("/root[1]/node[2]/@flag", DiffType.MODIFY)
                );

        List<MergeDecision> decisions = diff.getHunks().stream()
                .map(h -> new MergeDecision(h.getId(), MergeChoice.TAKE_RIGHT, null))
                .toList();
        MergeResult result = adapter.merge(leftContent, rightContent, decisions, output);

        assertThat(result.getOutputPath()).isEqualTo(output);
        String merged = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(merged).contains("status=\"new\"");
        assertThat(merged).contains("flag=\"yes\"");
    }

    @Test
    void diff_honoursSiblingIndexing() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.xml"),
                "<root><item>first</item><item>second</item><item>third</item></root>",
                StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.xml"),
                "<root><item>first</item><item>updated</item><item>third</item></root>",
                StandardCharsets.UTF_8);

        XmlFormatAdapter adapter = new XmlFormatAdapter();
        DiffResult diff = adapter.diff(
                adapter.normalize(new FileDescriptor(left, FormatType.XML, StandardCharsets.UTF_8)),
                adapter.normalize(new FileDescriptor(right, FormatType.XML, StandardCharsets.UTF_8))
        );

        assertThat(diff.getHunks())
                .extracting(h -> decodePointer(h.getId()), DiffHunk::getType)
                .containsExactly(tuple("/root[1]/item[2]/text()", DiffType.MODIFY));
    }

    @Test
    void normalize_ignoresWhitespaceOnlyTextNodes() throws Exception {
        Path xml = Files.writeString(tempDir.resolve("whitespace.xml"),
                "<root>\n  <item>\n    <name>value</name>\n  </item>\n</root>",
                StandardCharsets.UTF_8);

        XmlFormatAdapter adapter = new XmlFormatAdapter();
        NormalizedContent content = adapter.normalize(new FileDescriptor(xml, FormatType.XML, StandardCharsets.UTF_8));

        assertThat(content.getLogicalRecords())
                .noneMatch(record -> record.contains("text()") && record.trim().endsWith("="));
        assertThat(content.getLogicalRecords())
                .anyMatch(record -> record.contains("/name[1]/text() = value"));
    }

    @Test
    void parserConfigurationFailureWrappedAsXmlProcessingException() throws Exception {
        previousFactoryProperty = System.getProperty(DocumentBuilderFactory.class.getName());
        System.setProperty(DocumentBuilderFactory.class.getName(), FailingFactory.class.getName());

        Path xml = Files.writeString(tempDir.resolve("simple.xml"), "<root/>", StandardCharsets.UTF_8);
        XmlFormatAdapter adapter = new XmlFormatAdapter();

        assertThatThrownBy(() -> adapter.normalize(new FileDescriptor(xml, FormatType.XML, StandardCharsets.UTF_8)))
                .isInstanceOf(XmlProcessingException.class)
                .hasMessageContaining("XML parser configuration error");
    }

    private String decodePointer(String hunkId) {
        String encoded = hunkId.replace("xml-path-", "");
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    public static class FailingFactory extends DocumentBuilderFactory {
        public FailingFactory() {}

        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
            throw new ParserConfigurationException("forced failure");
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setFeature(String name, boolean value) {
        }

        @Override
        public boolean getFeature(String name) {
            return false;
        }

        @Override
        public void setNamespaceAware(boolean awareness) {
        }

        @Override
        public void setValidating(boolean validating) {
        }

        @Override
        public void setIgnoringElementContentWhitespace(boolean whitespace) {
        }

        @Override
        public void setExpandEntityReferences(boolean expandEntityRef) {
        }

        @Override
        public void setIgnoringComments(boolean ignoreComments) {
        }

        @Override
        public void setCoalescing(boolean coalescing) {
        }
    }
}

