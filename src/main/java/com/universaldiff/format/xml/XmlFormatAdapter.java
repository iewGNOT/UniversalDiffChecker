package com.universaldiff.format.xml;

import com.universaldiff.core.model.DiffFragment;
import com.universaldiff.core.model.DiffHunk;
import com.universaldiff.core.model.DiffResult;
import com.universaldiff.core.model.DiffSide;
import com.universaldiff.core.model.DiffType;
import com.universaldiff.core.model.FileDescriptor;
import com.universaldiff.core.model.FormatType;
import com.universaldiff.core.model.MergeChoice;
import com.universaldiff.core.model.MergeDecision;
import com.universaldiff.core.model.MergeResult;
import com.universaldiff.core.model.NormalizedContent;
import com.universaldiff.format.spi.FormatAdapter;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class XmlFormatAdapter implements FormatAdapter {

    @Override
    public NormalizedContent normalize(FileDescriptor descriptor) throws IOException {
        Document document = parse(descriptor.getPath());
        Map<String, String> flattened = new LinkedHashMap<>();
        flatten(document.getDocumentElement(), "/" + document.getDocumentElement().getNodeName() + "[1]", flattened);
        List<String> logical = flattened.entrySet().stream()
                .map(e -> e.getKey() + " = " + e.getValue())
                .toList();
        return NormalizedContent.builder(FormatType.XML)
                .logicalRecords(logical)
                .nativeModel(document)
                .encoding(descriptor.getEncoding())
                .build();
    }

    private Document parse(Path path) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(Files.newInputStream(path));
        } catch (Exception ex) {
            throw new IOException("Failed to parse XML", ex);
        }
    }

    private void flatten(Node node, String path, Map<String, String> result) {
        if (node == null) {
            return;
        }
        if (node.hasAttributes()) {
            NamedNodeMap attrs = node.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr attr = (Attr) attrs.item(i);
                result.put(path + "/@" + attr.getName(), attr.getValue());
            }
        }
        NodeList children = node.getChildNodes();
        Map<String, Integer> counters = new LinkedHashMap<>();
        boolean hasElementChildren = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                hasElementChildren = true;
                String name = child.getNodeName();
                int index = counters.getOrDefault(name, 0) + 1;
                counters.put(name, index);
                flatten(child, path + "/" + name + "[" + index + "]", result);
            }
        }
        if (!hasElementChildren) {
            String text = node.getTextContent().trim();
            if (!text.isEmpty()) {
                result.put(path + "/text()", text);
            }
        }
    }

    @Override
    public DiffResult diff(NormalizedContent left, NormalizedContent right) {
        Instant start = Instant.now();
        Map<String, String> leftMap = toMap(left);
        Map<String, String> rightMap = toMap(right);
        List<DiffHunk> hunks = new ArrayList<>();
        for (String path : leftMap.keySet()) {
            if (!rightMap.containsKey(path)) {
                hunks.add(DiffHunk.of(
                        "xml-path-" + encode(path),
                        DiffType.DELETE,
                        path,
                        List.of(new DiffFragment(DiffSide.LEFT, 0, 0, leftMap.get(path)))));
            } else if (!leftMap.get(path).equals(rightMap.get(path))) {
                hunks.add(DiffHunk.of(
                        "xml-path-" + encode(path),
                        DiffType.MODIFY,
                        path,
                        List.of(
                                new DiffFragment(DiffSide.LEFT, 0, 0, leftMap.get(path)),
                                new DiffFragment(DiffSide.RIGHT, 0, 0, rightMap.get(path))
                        )));
            }
        }
        for (String path : rightMap.keySet()) {
            if (!leftMap.containsKey(path)) {
                hunks.add(DiffHunk.of(
                        "xml-path-" + encode(path),
                        DiffType.INSERT,
                        path,
                        List.of(new DiffFragment(DiffSide.RIGHT, 0, 0, rightMap.get(path)))));
            }
        }
        return new DiffResult(FormatType.XML, hunks, Duration.between(start, Instant.now()));
    }

    private String encode(String path) {
        return java.util.Base64.getEncoder().encodeToString(path.getBytes());
    }

    private String decode(String encoded) {
        return new String(java.util.Base64.getDecoder().decode(encoded));
    }

    private Map<String, String> toMap(NormalizedContent content) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String record : content.getLogicalRecords()) {
            int sep = record.indexOf(" = ");
            if (sep > 0) {
                map.put(record.substring(0, sep), record.substring(sep + 3));
            }
        }
        return map;
    }

    @Override
    public MergeResult merge(NormalizedContent left,
                             NormalizedContent right,
                             List<MergeDecision> decisions,
                             Path outputPath) throws IOException {
        Instant start = Instant.now();
        Document leftDoc = ((Document) left.getNativeModel());
        Document merged = (Document) leftDoc.cloneNode(true);
        Document rightDoc = ((Document) right.getNativeModel());
        XPath xpath = XPathFactory.newInstance().newXPath();
        for (MergeDecision decision : decisions) {
            String encoded = decision.getHunkId().replace("xml-path-", "");
            String path = decode(encoded);
            switch (decision.getChoice()) {
                case TAKE_LEFT -> {
                    // no-op
                }
                case TAKE_RIGHT -> applyFromRight(merged, rightDoc, xpath, path);
                case MANUAL -> {
                    if (decision.getManualContent() != null) {
                        applyManual(merged, xpath, path, decision.getManualContent());
                    }
                }
            }
        }
        if (outputPath != null) {
            Files.writeString(outputPath, serialize(merged), left.getEncoding());
        }
        return new MergeResult(FormatType.XML, outputPath, Duration.between(start, Instant.now()));
    }

    private void applyFromRight(Document target, Document source, XPath xpath, String path) throws IOException {
        try {
            Node sourceNode = (Node) xpath.evaluate(path, source, XPathConstants.NODE);
            if (sourceNode == null) {
                return;
            }
            if (path.endsWith("/text()")) {
                Node targetNode = (Node) xpath.evaluate(parentPath(path), target, XPathConstants.NODE);
                if (targetNode != null) {
                    targetNode.setTextContent(sourceNode.getNodeValue());
                }
            } else if (path.contains("/@")) {
                Node targetNode = (Node) xpath.evaluate(path, target, XPathConstants.NODE);
                if (targetNode != null && targetNode.getNodeType() == Node.ATTRIBUTE_NODE) {
                    targetNode.setNodeValue(sourceNode.getNodeValue());
                }
            } else {
                Node targetNode = (Node) xpath.evaluate(path, target, XPathConstants.NODE);
                if (targetNode != null && targetNode.getParentNode() != null) {
                    Node imported = target.importNode(sourceNode, true);
                    targetNode.getParentNode().replaceChild(imported, targetNode);
                }
            }
        } catch (XPathExpressionException ex) {
            throw new IOException("Failed to apply XML merge", ex);
        }
    }

    private void applyManual(Document target, XPath xpath, String path, String value) throws IOException {
        try {
            if (path.endsWith("/text()")) {
                Node node = (Node) xpath.evaluate(parentPath(path), target, XPathConstants.NODE);
                if (node != null) {
                    node.setTextContent(value);
                }
            } else if (path.contains("/@")) {
                Node node = (Node) xpath.evaluate(path, target, XPathConstants.NODE);
                if (node != null && node.getNodeType() == Node.ATTRIBUTE_NODE) {
                    node.setNodeValue(value);
                }
            }
        } catch (XPathExpressionException ex) {
            throw new IOException("Failed to apply manual XML content", ex);
        }
    }

    private String parentPath(String path) {
        int idx = path.lastIndexOf('/');
        if (idx <= 0) {
            return path;
        }
        return path.substring(0, idx);
    }

    private String serialize(Document document) throws IOException {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException ex) {
            throw new IOException("Failed to serialize XML", ex);
        }
    }
}

