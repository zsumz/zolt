package com.zolt.explain.maven;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves Maven {@code ${...}} property references for the static migration audit.
 *
 * <p>Mirrors the interpolation contract of {@code PomPropertyInterpolator} in zolt-repository (POM
 * {@code <properties>} layered over the standard {@code project.*} / {@code parent.*} built-ins,
 * recursive resolution, cycle detection) but is decoupled from the resolver's raw-POM types so the
 * audit can interpolate versions, exclusion coordinates, and project fields before classification and
 * emit. Unlike the resolver, an unresolved or cyclic reference is not fatal here: {@link #interpolate}
 * returns the original literal unchanged so the caller can surface it as an honest review item rather
 * than misclassify it.
 */
final class MavenPomProperties {
    private final Map<String, String> properties;

    MavenPomProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    Map<String, String> values() {
        return properties;
    }

    /**
     * Resolves {@code ${...}} references in {@code value}. Returns the fully resolved string when every
     * reference resolves statically; otherwise returns {@code value} unchanged (a {@code null} input
     * becomes the empty string).
     */
    String interpolate(String value) {
        if (value == null || !value.contains("${")) {
            return value == null ? "" : value;
        }
        String resolved = interpolate(value, new LinkedHashSet<>());
        return resolved == null ? value : resolved;
    }

    private String interpolate(String value, Set<String> stack) {
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        while (cursor < value.length()) {
            int start = value.indexOf("${", cursor);
            if (start < 0) {
                result.append(value, cursor, value.length());
                break;
            }
            int end = value.indexOf('}', start + 2);
            if (end < 0) {
                // Malformed expression; keep the literal rather than guessing.
                return null;
            }
            result.append(value, cursor, start);
            String propertyName = value.substring(start + 2, end);
            if (stack.contains(propertyName)) {
                return null;
            }
            String replacement = properties.get(propertyName);
            if (replacement == null) {
                return null;
            }
            stack.add(propertyName);
            String resolvedReplacement = interpolate(replacement, stack);
            stack.remove(propertyName);
            if (resolvedReplacement == null) {
                return null;
            }
            result.append(resolvedReplacement);
            cursor = end + 1;
        }
        return result.toString();
    }
}
