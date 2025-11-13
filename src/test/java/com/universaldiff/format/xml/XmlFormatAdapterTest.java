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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlFormatAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void diff_reportsTextChange() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.xml"), "<root><node>1</node></root>", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.xml"), "<root><node>2</node></root>", StandardCharsets.UTF_8);

        XmlFormatAdapter adapter = new XmlFormatAdapter();
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.XML, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.XML, StandardCharsets.UTF_8));

        DiffResult diff = adapter.diff(leftContent, rightContent);
        assertThat(diff.getHunks()).hasSize(1);
        DiffHunk hunk = diff.getHunks().get(0);
        assertThat(hunk.getId()).startsWith("xml-path-");
        assertThat(hunk.getType()).isEqualTo(DiffType.MODIFY);
    }

    @Test
    void merge_takeRightUpdatesText() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left.xml"), "<root><node>1</node></root>", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right.xml"), "<root><node>2</node></root>", StandardCharsets.UTF_8);
        Path output = tempDir.resolve("merged.xml");

        XmlFormatAdapter adapter = new XmlFormatAdapter();
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.XML, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.XML, StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        List<MergeDecision> decisions = diff.getHunks().stream()
                .map(h -> new MergeDecision(h.getId(), MergeChoice.TAKE_RIGHT, null))
                .collect(Collectors.toList());

        MergeResult result = adapter.merge(leftContent, rightContent, decisions, output);

        assertThat(result.getOutputPath()).isEqualTo(output);
        assertThat(Files.readString(output, StandardCharsets.UTF_8)).contains("<node>2</node>");
    }

    @Test
    void normalize_malformedXmlThrowsDomainException() throws Exception {
        Path invalid = Files.writeString(tempDir.resolve("bad.xml"), "<root><missing></root>", StandardCharsets.UTF_8);
        XmlFormatAdapter adapter = new XmlFormatAdapter();

        assertThatThrownBy(() ->
                adapter.normalize(new FileDescriptor(invalid, FormatType.XML, StandardCharsets.UTF_8)))
                .isInstanceOf(XmlProcessingException.class);
    }

    @Test
    void merge_takeRightHandlesAttributesAndNewElements() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left-attr.xml"),
                "<root><item id=\"a\">old</item><extra>false</extra></root>", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right-attr.xml"),
                "<root><item id=\"b\">new</item><extra>true</extra></root>", StandardCharsets.UTF_8);
        Path output = tempDir.resolve("attr-merged.xml");

        XmlFormatAdapter adapter = new XmlFormatAdapter();
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.XML, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.XML, StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        List<MergeDecision> decisions = diff.getHunks().stream()
                .map(h -> new MergeDecision(h.getId(), MergeChoice.TAKE_RIGHT, null))
                .collect(Collectors.toList());

        adapter.merge(leftContent, rightContent, decisions, output);

        String merged = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(merged).contains("id=\"b\"")
                .contains("<item id=\"b\">new</item>")
                .contains("<extra>true</extra>");
    }

    @Test
    void merge_manualOverrideChangesTextContent() throws Exception {
        Path left = Files.writeString(tempDir.resolve("left-manual.xml"),
                "<root><value>left</value></root>", StandardCharsets.UTF_8);
        Path right = Files.writeString(tempDir.resolve("right-manual.xml"),
                "<root><value>right</value></root>", StandardCharsets.UTF_8);
        Path output = tempDir.resolve("manual-merged.xml");

        XmlFormatAdapter adapter = new XmlFormatAdapter();
        NormalizedContent leftContent = adapter.normalize(new FileDescriptor(left, FormatType.XML, StandardCharsets.UTF_8));
        NormalizedContent rightContent = adapter.normalize(new FileDescriptor(right, FormatType.XML, StandardCharsets.UTF_8));
        DiffResult diff = adapter.diff(leftContent, rightContent);

        MergeDecision manual = new MergeDecision(diff.getHunks().get(0).getId(), MergeChoice.MANUAL, "custom");
        adapter.merge(leftContent, rightContent, List.of(manual), output);

        assertThat(Files.readString(output, StandardCharsets.UTF_8)).contains("<value>custom</value>");
    }
}
