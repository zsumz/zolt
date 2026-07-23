package sh.zolt.maven.metadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parses {@code maven-metadata.xml} version listings with hardened XML configuration mirroring the
 * POM parser: secure processing on, DOCTYPE declarations disallowed, external entities and external
 * DTD/schema access disabled. Reads {@code <metadata><versioning><versions><version>}; the
 * {@code <latest>}/{@code <release>} hints are deliberately ignored.
 */
public final class MavenMetadataParser {
    public MavenMetadata parse(String xml) {
        return parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public MavenMetadata parse(byte[] bytes) {
        return parse(new ByteArrayInputStream(bytes));
    }

    public MavenMetadata parse(InputStream inputStream) {
        Document document = document(inputStream);
        Element metadata = document.getDocumentElement();
        if (metadata == null || !hasName(metadata, "metadata")) {
            throw new MavenMetadataParseException(
                    "Could not parse maven-metadata.xml. Expected root <metadata> element.");
        }
        Optional<Element> versions = child(metadata, "versioning").flatMap(versioning -> child(versioning, "versions"));
        if (versions.isEmpty()) {
            return new MavenMetadata(List.of());
        }
        List<String> collected = new ArrayList<>();
        for (Element version : children(versions.orElseThrow(), "version")) {
            String text = version.getTextContent();
            if (text != null && !text.trim().isBlank()) {
                collected.add(text.trim());
            }
        }
        return new MavenMetadata(collected);
    }

    private Document document(InputStream inputStream) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new QuietErrorHandler());
            return builder.parse(inputStream);
        } catch (ParserConfigurationException exception) {
            throw new MavenMetadataParseException("Could not configure secure maven-metadata.xml parser.", exception);
        } catch (SAXException exception) {
            throw new MavenMetadataParseException(
                    "Could not parse maven-metadata.xml. Fix malformed XML before discovering versions.",
                    exception);
        } catch (IOException exception) {
            throw new MavenMetadataParseException("Could not read maven-metadata.xml input.", exception);
        }
    }

    private static Optional<Element> child(Element parent, String childName) {
        for (Element child : children(parent)) {
            if (hasName(child, childName)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    private static List<Element> children(Element parent, String childName) {
        return children(parent).stream()
                .filter(child -> hasName(child, childName))
                .toList();
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < parent.getChildNodes().getLength(); index++) {
            Node node = parent.getChildNodes().item(index);
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static boolean hasName(Element element, String expected) {
        return expected.equals(name(element));
    }

    private static String name(Element element) {
        String localName = element.getLocalName();
        return localName == null ? element.getNodeName() : localName;
    }

    private static final class QuietErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
