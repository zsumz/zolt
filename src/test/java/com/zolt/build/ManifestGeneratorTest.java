package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zolt.project.ProjectMetadata;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
