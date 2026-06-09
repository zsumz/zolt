package com.zolt.maven;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

public final class RawPomParser {
    public RawPom parse(String xml) {
        return parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public RawPom parse(byte[] bytes) {
        return parse(new ByteArrayInputStream(bytes));
    }

    public RawPom parse(InputStream inputStream) {
        Document document = document(inputStream);
        Element project = document.getDocumentElement();
        if (project == null || !hasName(project, "project")) {
            throw new RawPomParseException("Could not parse POM XML. Expected root <project> element.");
        }

        Optional<RawPomParent> parent = child(project, "parent").map(this::parseParent);
        Optional<RawPomRelocation> relocation = parseRelocation(project);
        return new RawPom(
                text(project, "groupId"),
                requiredText(project, "artifactId", "project"),
                text(project, "version"),
                text(project, "packaging").orElse("jar"),
                parent,
                relocation,
                parseProperties(project),
                parseDependencyManagement(project),
                parseDependencies(child(project, "dependencies")));
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
            throw new RawPomParseException("Could not configure secure POM XML parser.", exception);
        } catch (SAXException exception) {
            throw new RawPomParseException(
                    "Could not parse POM XML. Fix malformed XML before resolving this dependency.",
                    exception);
        } catch (IOException exception) {
            throw new RawPomParseException("Could not read POM XML input.", exception);
        }
    }

    private RawPomParent parseParent(Element parent) {
        return new RawPomParent(
                requiredText(parent, "groupId", "parent"),
                requiredText(parent, "artifactId", "parent"),
                requiredText(parent, "version", "parent"),
                text(parent, "relativePath"));
    }

    private Optional<RawPomRelocation> parseRelocation(Element project) {
        return child(project, "distributionManagement")
                .flatMap(distributionManagement -> child(distributionManagement, "relocation"))
                .map(relocation -> new RawPomRelocation(
                        text(relocation, "groupId"),
                        text(relocation, "artifactId"),
                        text(relocation, "version"),
                        text(relocation, "message")));
    }

    private Map<String, String> parseProperties(Element project) {
        Optional<Element> properties = child(project, "properties");
        if (properties.isEmpty()) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (Element property : children(properties.orElseThrow())) {
            values.put(name(property), property.getTextContent().trim());
        }
        return values;
    }

    private List<RawPomDependency> parseDependencyManagement(Element project) {
        return child(project, "dependencyManagement")
                .flatMap(element -> child(element, "dependencies"))
                .map(this::parseDependenciesElement)
                .orElseGet(List::of);
    }

    private List<RawPomDependency> parseDependencies(Optional<Element> dependencies) {
        if (dependencies.isEmpty()) {
            return List.of();
        }
        return parseDependenciesElement(dependencies.orElseThrow());
    }

    private List<RawPomDependency> parseDependenciesElement(Element dependencies) {
        List<RawPomDependency> parsed = new ArrayList<>();
        for (Element dependency : children(dependencies, "dependency")) {
            parsed.add(parseDependency(dependency));
        }
        return parsed;
    }

    private RawPomDependency parseDependency(Element dependency) {
        return new RawPomDependency(
                requiredText(dependency, "groupId", "dependency"),
                requiredText(dependency, "artifactId", "dependency"),
                text(dependency, "version"),
                text(dependency, "scope"),
                text(dependency, "type"),
                text(dependency, "classifier"),
                text(dependency, "optional").map(Boolean::parseBoolean).orElse(false),
                parseExclusions(dependency));
    }

    private List<RawPomExclusion> parseExclusions(Element dependency) {
        Optional<Element> exclusions = child(dependency, "exclusions");
        if (exclusions.isEmpty()) {
            return List.of();
        }

        List<RawPomExclusion> parsed = new ArrayList<>();
        for (Element exclusion : children(exclusions.orElseThrow(), "exclusion")) {
            parsed.add(new RawPomExclusion(
                    requiredText(exclusion, "groupId", "exclusion"),
                    requiredText(exclusion, "artifactId", "exclusion")));
        }
        return parsed;
    }

    private static String requiredText(Element parent, String childName, String section) {
        return text(parent, childName).orElseThrow(() -> new RawPomParseException(
                "Could not parse POM XML. Missing required <" + childName + "> in <" + section + ">."));
    }

    private static Optional<String> text(Element parent, String childName) {
        return child(parent, childName)
                .map(Element::getTextContent)
                .map(String::trim)
                .filter(value -> !value.isBlank());
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
