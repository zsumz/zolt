package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.ProjectMetadata;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

final class ManifestGeneratorTest {
    private final ManifestGenerator generator = new ManifestGenerator();

    @Test
    void manifestIncludesMainClassWhenProjectMainIsConfigured() throws IOException {
        GeneratedManifest generated = generator.generate(project(Optional.of("com.example.Main")));
        Manifest manifest = parse(generated);
        Attributes attributes = manifest.getMainAttributes();

        assertEquals(GeneratedManifest.DEFAULT_PATH, generated.path());
        assertEquals("1.0", attributes.getValue(Attributes.Name.MANIFEST_VERSION));
        assertEquals("com.example.Main", attributes.getValue(Attributes.Name.MAIN_CLASS));
        assertEquals("com.example.Main", generated.mainClass().orElseThrow());
    }

    @Test
    void libraryProjectsGetManifestWithoutMainClass() throws IOException {
        GeneratedManifest generated = generator.generate(project(Optional.empty()));
        Manifest manifest = parse(generated);
        Attributes attributes = manifest.getMainAttributes();

        assertEquals("1.0", attributes.getValue(Attributes.Name.MANIFEST_VERSION));
        assertFalse(generated.mainClass().isPresent());
        assertFalse(attributes.containsKey(Attributes.Name.MAIN_CLASS));
    }

    @Test
    void includesSortedCustomManifestAttributes() throws IOException {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("Bundle-SymbolicName", "com.example.demo");
        attributes.put("Automatic-Module-Name", "com.example.demo");

        GeneratedManifest generated = generator.generate(project(Optional.of("com.example.Main")), attributes);
        Manifest manifest = parse(generated);
        Attributes mainAttributes = manifest.getMainAttributes();
        String content = new String(generated.content(), StandardCharsets.UTF_8);

        assertEquals("com.example.demo", mainAttributes.getValue("Automatic-Module-Name"));
        assertEquals("com.example.demo", mainAttributes.getValue("Bundle-SymbolicName"));
        assertTrue(content.indexOf("Automatic-Module-Name") < content.indexOf("Bundle-SymbolicName"));
        assertTrue(content.indexOf("Main-Class") < content.indexOf("Automatic-Module-Name"));
    }

    @Test
    void foldsLongManifestAttributeValuesDeterministically() throws IOException {
        GeneratedManifest generated = generator.generate(
                project(Optional.empty()),
                Map.of("Export-Package", "com.example.alpha;version=\"1.0.0\",com.example.beta;version=\"1.0.0\""));

        Manifest manifest = parse(generated);
        String content = new String(generated.content(), StandardCharsets.UTF_8);

        assertEquals(
                "com.example.alpha;version=\"1.0.0\",com.example.beta;version=\"1.0.0\"",
                manifest.getMainAttributes().getValue("Export-Package"));
        assertTrue(content.contains("\r\n "));
    }

    @Test
    void rejectsManifestDefaultsAsCustomAttributes() {
        ManifestGenerationException exception = assertThrows(
                ManifestGenerationException.class,
                () -> generator.generate(project(Optional.empty()), Map.of("Main-Class", "com.example.Other")));

        assertTrue(exception.getMessage().contains("Zolt owns Manifest-Version and Main-Class"));
    }

    @Test
    void rejectsMalformedManifestAttributeNames() {
        ManifestGenerationException exception = assertThrows(
                ManifestGenerationException.class,
                () -> generator.generate(project(Optional.empty()), Map.of("Bad Name", "value")));

        assertTrue(exception.getMessage().contains("Manifest attribute names must contain only"));
    }

    @Test
    void rejectsUnsafeManifestAttributeValues() {
        ManifestGenerationException exception = assertThrows(
                ManifestGenerationException.class,
                () -> generator.generate(project(Optional.empty()), Map.of("Bundle-Name", "bad\nvalue")));

        assertTrue(exception.getMessage().contains("Manifest values cannot contain line breaks"));
    }

    @Test
    void rejectsCaseInsensitiveDuplicateManifestAttributeNames() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("Bundle-Name", "demo");
        attributes.put("bundle-name", "duplicate");

        ManifestGenerationException exception = assertThrows(
                ManifestGenerationException.class,
                () -> generator.generate(project(Optional.empty()), attributes));

        assertTrue(exception.getMessage().contains("unique ignoring case"));
    }

    @Test
    void contentAccessorReturnsDefensiveCopy() {
        GeneratedManifest generated = generator.generate(project(Optional.of("com.example.Main")));
        byte[] first = generated.content();
        first[0] = 0;

        assertArrayEquals(generator.generate(project(Optional.of("com.example.Main"))).content(), generated.content());
    }

    private static ProjectMetadata project(Optional<String> mainClass) {
        return new ProjectMetadata("demo", "0.1.0", "com.example", "21", mainClass);
    }

    private static Manifest parse(GeneratedManifest generated) throws IOException {
        return new Manifest(new ByteArrayInputStream(generated.content()));
    }
}
