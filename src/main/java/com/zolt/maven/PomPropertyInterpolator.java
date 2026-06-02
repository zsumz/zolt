package com.zolt.maven;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PomPropertyInterpolator {
    public String interpolate(String value, EffectiveRawPom pom) {
        Map<String, String> properties = properties(pom);
        String context = pom.groupId() + ":" + pom.rawPom().artifactId() + ":" + pom.version();
        return interpolate(value, properties, context);
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

    private String interpolate(String value, Map<String, String> properties, String context) {
        String result = value;
        int start = result.indexOf("${");
        while (start >= 0) {
            int end = result.indexOf('}', start + 2);
            if (end < 0) {
                throw new PomInterpolationException(
                        "Unclosed property expression in POM for " + context + ": `" + value + "`.");
            }

            String propertyName = result.substring(start + 2, end);
            String replacement = properties.get(propertyName);
            if (replacement == null) {
                throw new PomInterpolationException(
                        "Unresolved POM property `${"
                                + propertyName
                                + "}` while processing "
                                + context
                                + ". Define the property or declare the value explicitly.");
            }
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
        return properties;
    }
}
