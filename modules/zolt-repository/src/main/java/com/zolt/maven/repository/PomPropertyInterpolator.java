package com.zolt.maven.repository;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PomPropertyInterpolator {
    public String interpolate(String value, EffectiveRawPom pom) {
        if (!containsPropertyExpression(value)) {
            return value;
        }
        Map<String, String> properties = properties(pom);
        String context = context(pom);
        return interpolate(value, properties, context);
    }

    public RawPomDependency interpolateDependency(RawPomDependency dependency, EffectiveRawPom pom) {
        if (!containsPropertyExpression(dependency)) {
            return dependency;
        }
        if (dependency.classifier().map(PomPropertyInterpolator::containsPropertyExpression).orElse(false)) {
            throw new PomInterpolationException(
                    "Dynamic classifier selection `"
                            + dependency.classifier().orElseThrow()
                            + "` is not supported in the public beta while processing "
                            + context(pom)
                            + ". Declare a fixed OS/architecture classifier artifact, or keep this dependency outside the Zolt beta scope.");
        }
        Map<String, String> properties = properties(pom);
        String context = context(pom);
        return new RawPomDependency(
                interpolate(dependency.groupId(), properties, context),
                interpolate(dependency.artifactId(), properties, context),
                dependency.version().map(value -> interpolate(value, properties, context)),
                dependency.scope().map(value -> interpolate(value, properties, context)),
                dependency.type().map(value -> interpolate(value, properties, context)),
                dependency.classifier().map(value -> interpolate(value, properties, context)),
                dependency.optional(),
                dependency.exclusions().stream()
                        .map(exclusion -> new RawPomExclusion(
                                interpolate(exclusion.groupId(), properties, context),
                                interpolate(exclusion.artifactId(), properties, context)))
                        .toList());
    }

    public RawPomRelocation interpolateRelocation(RawPomRelocation relocation, EffectiveRawPom pom) {
        if (!containsPropertyExpression(relocation)) {
            return relocation;
        }
        Map<String, String> properties = properties(pom);
        String context = context(pom);
        return new RawPomRelocation(
                relocation.groupId().map(value -> interpolate(value, properties, context)),
                relocation.artifactId().map(value -> interpolate(value, properties, context)),
                relocation.version().map(value -> interpolate(value, properties, context)),
                relocation.message().map(value -> interpolate(value, properties, context)));
    }

    private String interpolate(String value, Map<String, String> properties, String context) {
        if (!containsPropertyExpression(value)) {
            return value;
        }
        return interpolate(value, properties, context, new LinkedHashSet<>());
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

    private static boolean containsPropertyExpression(RawPomDependency dependency) {
        return containsPropertyExpression(dependency.groupId())
                || containsPropertyExpression(dependency.artifactId())
                || dependency.version().map(PomPropertyInterpolator::containsPropertyExpression).orElse(false)
                || dependency.scope().map(PomPropertyInterpolator::containsPropertyExpression).orElse(false)
                || dependency.type().map(PomPropertyInterpolator::containsPropertyExpression).orElse(false)
                || dependency.classifier().map(PomPropertyInterpolator::containsPropertyExpression).orElse(false)
                || dependency.exclusions().stream()
                        .anyMatch(exclusion -> containsPropertyExpression(exclusion.groupId())
                                || containsPropertyExpression(exclusion.artifactId()));
    }

    private static boolean containsPropertyExpression(RawPomRelocation relocation) {
        return relocation.groupId().map(PomPropertyInterpolator::containsPropertyExpression).orElse(false)
                || relocation.artifactId().map(PomPropertyInterpolator::containsPropertyExpression).orElse(false)
                || relocation.version().map(PomPropertyInterpolator::containsPropertyExpression).orElse(false)
                || relocation.message().map(PomPropertyInterpolator::containsPropertyExpression).orElse(false);
    }

    private static boolean containsPropertyExpression(String value) {
        return value.contains("${");
    }

    private static String context(EffectiveRawPom pom) {
        return pom.groupId() + ":" + pom.rawPom().artifactId() + ":" + pom.version();
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
