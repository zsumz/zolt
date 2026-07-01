package com.zolt.explain.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Namespace-tolerant DOM helpers shared by the Maven POM inspection code. */
final class MavenXml {
    private MavenXml() {
    }

    static Optional<Element> child(Element parent, String childName) {
        for (Element child : children(parent)) {
            if (hasName(child, childName)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    static List<String> texts(Optional<Element> parent, String childName) {
        if (parent.isEmpty()) {
            return List.of();
        }
        return children(parent.orElseThrow(), childName).stream()
                .map(Element::getTextContent)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    static Optional<String> text(Element parent, String childName) {
        return child(parent, childName)
                .map(Element::getTextContent)
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }

    static List<Element> children(Element parent, String childName) {
        return children(parent).stream()
                .filter(child -> hasName(child, childName))
                .toList();
    }

    static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < parent.getChildNodes().getLength(); index++) {
            Node node = parent.getChildNodes().item(index);
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    static boolean hasName(Element element, String expected) {
        return expected.equals(name(element));
    }

    static String name(Element element) {
        String localName = element.getLocalName();
        return localName == null ? element.getNodeName() : localName;
    }
}
