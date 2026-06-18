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
        assertTrue(readiness.contains("Kotlin, Scala, Android, SNAPSHOTs, and version ranges in the public MVP"));
        assertFalse(
                readiness.contains("Kotlin JVM source support."),
                "Kotlin must stay out of planned public-MVP readiness docs");
        assertFalse(
                readiness.contains("- [ ] Public readiness docs list supported, experimental, planned, and non-goal surfaces."),
                "Release readiness should not show the public support-surface docs item as incomplete");
    }

    @Test
    void adoptionReadinessDoesNotCallImplementedGuardrailsBlockers() throws IOException {
        String readiness = Files.readString(Path.of("docs/adoption-readiness.md"));

        assertTrue(readiness.contains("Core adoption guardrails:"));
        assertTrue(readiness.contains(""));
        assertTrue(readiness.contains(""));
        assertTrue(readiness.contains(""));
        assertFalse(
                readiness.contains("Top adoption blockers:"),
                "Implemented cache, stale-lock, and smoke guardrails should not be labeled as active blockers");
    }

    @Test
    void testingStrategyKeepsKotlinPostMvp() throws IOException {
        String testingStrategy = Files.readString(Path.of("docs/testing-strategy.md"));

        assertTrue(testingStrategy.contains("Kotlin remains future post-MVP source-language work"));
        assertTrue(testingStrategy.contains("should not be treated as public-beta test support"));
        assertFalse(
                testingStrategy.contains("Kotlin JVM source support, and multiple test engines continue to grow"),
                "Testing strategy must not imply Kotlin is growing as current public-beta support");
    }

    @Test
    void commandDocsDescribeTypedFrameworkNativeMigration() throws IOException {
        String commands = Files.readString(Path.of("docs/commands.md"));

        assertTrue(commands.contains("external framework AOT/native/dev-mode tasks"));
        assertTrue(commands.contains("typed Zolt framework settings such as `[framework.springBoot.native] enabled = true`"));
        assertTrue(commands.contains("instead of executing Maven or Gradle native tasks"));
        assertFalse(
                commands.contains("framework-native modes require dedicated Zolt support before they are accepted"),
                "Command docs must not contradict the explicit Spring Boot native path");
    }

    @Test
    void releasePackagingDoesNotPretendLicenseExists() throws IOException {
        String releasePackaging = Files.readString(Path.of("docs/release-packaging.md"));

        assertTrue(releasePackaging.contains("LICENSE (only after the repository has a license file)"));
        assertTrue(releasePackaging.contains("release archives should not pretend to include a license"));
        assertFalse(
                releasePackaging.contains("└── LICENSE\n```"),
                "Archive layout must not show LICENSE as unconditional before a license decision");
    }

    @Test
    void productVisionDoesNotClaimGradleMavenBootstrapForV01() throws IOException {
        String productVision = Files.readString(Path.of("docs/product-vision.md"));

        assertTrue(productVision.contains("Gradle-free JVM bootstrap"));
        assertTrue(productVision.contains("Zolt builds, tests, packages, smokes, and parity-checks itself"));
        assertFalse(
                productVision.contains("v0.1: built with Gradle/Maven bootstrap"),
                "Product vision must not describe v0.1 as Gradle/Maven-bootstrapped after Zolt-owned self-hosting landed");
    }
}
