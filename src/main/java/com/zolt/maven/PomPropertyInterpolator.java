package com.zolt.maven;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PomPropertyInterpolator {
    public String interpolate(String value, EffectiveRawPom pom) {
        Map<String, String> properties = properties(pom);
        String context = pom.groupId() + ":" + pom.rawPom().artifactId() + ":" + pom.version();
        return interpolate(value, properties, context, new LinkedHashSet<>());
    }

    public RawPomDependency interpolateDependency(RawPomDependency dependency, EffectiveRawPom pom) {
        return new RawPomDependency(
                interpolate(dependency.groupId(), pom),
                interpolate(dependency.artifactId(), pom),
                dependency.version().map(value -> interpolate(value, pom)),
                dependency.scope().map(value -> interpolate(value, pom)),
                dependency.type().map(value -> interpolate(value, pom)),
                dependency.classifier().map(value -> interpolate(value, pom)),
                dependency.optional(),
                dependency.exclusions().stream()
                        .map(exclusion -> new RawPomExclusion(
                                interpolate(exclusion.groupId(), pom),
                                interpolate(exclusion.artifactId(), pom)))
                        .toList());
    }

    private String interpolate(String value, Map<String, String> properties, String context, Set<String> propertyStack) {
        String result = value;
        int start = result.indexOf("${");
        while (start >= 0) {
            int end = result.indexOf('}', start + 2);
            if (end < 0) {
                throw new PomInterpolationException(
                        "Unclosed property expression in POM for " + context + ": `" + value + "`.");
            }

            String propertyName = result.substring(start + 2, end);
            if (propertyStack.contains(propertyName)) {
                throw new PomInterpolationException(
                        "Cyclic POM property reference while processing "
                                + context
                                + ": "
                                + String.join(" -> ", propertyStack)
                                + " -> "
                                + propertyName
                                + ".");
            }
            String replacement = properties.get(propertyName);
            if (replacement == null) {
                throw new PomInterpolationException(
                        "Unresolved POM property `${"
                                + propertyName
                                + "}` while processing "
                                + context
                                + ". Define the property or declare the value explicitly.");
            }
            propertyStack.add(propertyName);
            replacement = interpolate(replacement, properties, context, propertyStack);
            propertyStack.remove(propertyName);
            result = result.substring(0, start) + replacement + result.substring(end + 1);
            start = result.indexOf("${", start + replacement.length());
        }
        return result;
    }

    private Map<String, String> properties(EffectiveRawPom pom) {
        Map<String, String> properties = new LinkedHashMap<>(pom.properties());
        properties.put("project.groupId", pom.groupId());
        properties.put("pom.groupId", pom.groupId());
        properties.put("groupId", pom.groupId());
        properties.put("project.artifactId", pom.rawPom().artifactId());
        properties.put("pom.artifactId", pom.rawPom().artifactId());
        properties.put("artifactId", pom.rawPom().artifactId());
        properties.put("project.version", pom.version());
        properties.put("pom.version", pom.version());
        properties.put("version", pom.version());
        pom.rawPom().parent().ifPresent(parent -> {
            properties.put("project.parent.groupId", parent.groupId());
            properties.put("pom.parent.groupId", parent.groupId());
            properties.put("parent.groupId", parent.groupId());
            properties.put("project.parent.artifactId", parent.artifactId());
            properties.put("pom.parent.artifactId", parent.artifactId());
            properties.put("parent.artifactId", parent.artifactId());
            properties.put("project.parent.version", parent.version());
            properties.put("pom.parent.version", parent.version());
            properties.put("parent.version", parent.version());
        });
        return properties;
    }
}
