package com.zolt.build;

import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;

public final class ManifestGenerator {
    private static final int MAX_MANIFEST_LINE_BYTES = 72;
    private static final String CONTINUATION_PREFIX = " ";

    public GeneratedManifest generate(ProjectConfig config) {
        return generate(config.project(), config.packageSettings().manifestAttributes());
    }

    public GeneratedManifest generateWithoutMain(ProjectConfig config) {
        return generate(config.project(), config.packageSettings().manifestAttributes(), false);
    }

    public GeneratedManifest generate(ProjectMetadata project) {
        return generate(project, Map.of());
    }

    public GeneratedManifest generate(ProjectMetadata project, Map<String, String> manifestAttributes) {
        return generate(project, manifestAttributes, true);
    }

    private GeneratedManifest generate(
            ProjectMetadata project,
            Map<String, String> manifestAttributes,
            boolean includeMainClass) {
        Map<String, String> attributes = validateAttributes(manifestAttributes);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeAttribute(output, Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
        if (includeMainClass) {
            project.main().ifPresent(mainClass -> writeAttribute(output, Attributes.Name.MAIN_CLASS.toString(), mainClass));
        }
        attributes.forEach((name, value) -> writeAttribute(output, name, value));
        output.write('\r');
        output.write('\n');

        return new GeneratedManifest(
                GeneratedManifest.DEFAULT_PATH,
                output.toByteArray(),
                includeMainClass ? project.main() : java.util.Optional.empty());
    }

    private static Map<String, String> validateAttributes(Map<String, String> manifestAttributes) {
        if (manifestAttributes == null || manifestAttributes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> entry : manifestAttributes.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            validateName(name);
            validateValue(name, value);
            String previous = sorted.put(name, value);
            if (previous != null) {
                throw new ManifestGenerationException(
                        "Invalid [package.manifest]."
                                + name
                                + " in zolt.toml. Manifest attribute names must be unique ignoring case.");
            }
        }
        return sorted;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ManifestGenerationException(
                    "Invalid [package.manifest] attribute name in zolt.toml. Use a non-empty manifest attribute name.");
        }
        if (name.equalsIgnoreCase(Attributes.Name.MANIFEST_VERSION.toString())
                || name.equalsIgnoreCase(Attributes.Name.MAIN_CLASS.toString())) {
            throw new ManifestGenerationException(
                    "Invalid [package.manifest]."
                            + name
                            + " in zolt.toml. Zolt owns Manifest-Version and Main-Class; use [project].main for Main-Class.");
        }
        try {
            new Attributes.Name(name);
        } catch (IllegalArgumentException exception) {
            throw new ManifestGenerationException(
                    "Invalid [package.manifest]."
                            + name
                            + " in zolt.toml. Manifest attribute names must contain only letters, digits, underscore, or hyphen and be at most 70 characters.",
                    exception);
        }
    }

    private static void validateValue(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new ManifestGenerationException(
                    "Invalid [package.manifest]."
                            + name
                            + " in zolt.toml. Use a non-empty string value.");
        }
        if (value.contains("\n") || value.contains("\r") || value.indexOf('\0') >= 0) {
            throw new ManifestGenerationException(
                    "Invalid [package.manifest]."
                            + name
                            + " in zolt.toml. Manifest values cannot contain line breaks or NUL characters.");
        }
    }

    private static void writeAttribute(ByteArrayOutputStream output, String name, String value) {
        String prefix = name + ": ";
        StringBuilder line = new StringBuilder(prefix);
        int lineBytes = byteLength(prefix);
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String text = new String(Character.toChars(codePoint));
            int textBytes = byteLength(text);
            if (lineBytes + textBytes > MAX_MANIFEST_LINE_BYTES) {
                writePhysicalLine(output, line.toString());
                line = new StringBuilder(CONTINUATION_PREFIX);
                lineBytes = byteLength(CONTINUATION_PREFIX);
            }
            line.append(text);
            lineBytes += textBytes;
            offset += Character.charCount(codePoint);
        }
        writePhysicalLine(output, line.toString());
    }

    private static int byteLength(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void writePhysicalLine(ByteArrayOutputStream output, String line) {
        output.writeBytes(line.getBytes(StandardCharsets.UTF_8));
        output.write('\r');
        output.write('\n');
    }
}
