package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PublicReadinessDocumentationTest {
    @Test
    void readmeDoesNotPublishStaleSpringBootNativeStatus() throws IOException {
        String readme = Files.readString(Path.of("README.md"));

        assertFalse(
                readme.contains("Spring Boot AOT/native is explicitly unsupported in the public beta"),
                "README must not contradict the explicit Spring Boot AOT/native canary path");
        assertTrue(
                readme.contains("[framework.springBoot.native] enabled = true"),
                "README should name the explicit Spring Boot native support flag");
        assertTrue(
                readme.contains("broader real-app AOT/native coverage is still beta-hardening work"),
                "README should keep the remaining Spring Boot native support limit visible");
    }

    @Test
    void adoptionReadinessListsPublicSupportSurfaceBoundaries() throws IOException {
        String readiness = Files.readString(Path.of("docs/adoption-readiness.md"));

        assertTrue(readiness.contains("## Public Support Surface"));
        assertTrue(readiness.contains("| Plain Java applications | Supported |"));
        assertTrue(readiness.contains("| Spring Boot AOT/native | Supported canary path |"));
        assertTrue(readiness.contains("| Quarkus JVM applications | Experimental |"));
        assertTrue(readiness.contains("| Groovy and Spock | Planned |"));
        assertTrue(readiness.contains("Non-goal for public beta"));
        assertFalse(
                readiness.contains("- [ ] Public readiness docs list supported, experimental, planned, and non-goal surfaces."),
                "Release readiness should not show the public support-surface docs item as incomplete");
    }
}
