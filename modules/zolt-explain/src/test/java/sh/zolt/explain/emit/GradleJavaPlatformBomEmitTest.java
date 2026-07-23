package sh.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.gradle.GradleStaticProjectInspector;
import sh.zolt.project.BomSettings;
import sh.zolt.project.PackageMode;
import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies a Gradle {@code java-platform} project drafts a {@code [bom]} member: {@code platform(...)}
 * imports to {@code [bom.imports]}, {@code constraints { }} pins to {@code [bom.versions]}, no
 * dependency/build scaffolding. Mirrors {@link MavenBomEmitTest}.
 */
final class GradleJavaPlatformBomEmitTest {
    @TempDir
    private Path tempDir;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    @Test
    void groovyJavaPlatformDraftsBomMember() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'acme-bom'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins {
                    id 'java-platform'
                }
                group = 'com.acme.platform'
                version = '1.0.0'
                javaPlatform {
                    allowDependencies()
                }
                dependencies {
                    api platform('com.fasterxml.jackson:jackson-bom:2.18.2')
                    api 'org.postgresql:postgresql:42.7.4'
                    constraints {
                        api 'com.example:lib-a:1.2.0'
                        runtime 'com.example:lib-b:3.4.5'
                    }
                }
                """);

        DraftZoltToml draft = draft();
        ProjectConfig config = draft.config();

        assertEquals(PackageMode.BOM, config.packageSettings().mode());
        BomSettings bom = config.packageSettings().bom();
        // The platform() import becomes a [bom.imports] entry.
        assertTrue(bom.imports().stream().anyMatch(imported ->
                        imported.coordinate().equals("com.fasterxml.jackson:jackson-bom")
                                && imported.version().equals("2.18.2")),
                () -> "platform() import must become a [bom.imports] entry: " + bom.imports());
        // The constraints become [bom.versions] entries (api and runtime alike).
        assertTrue(bom.versions().stream().anyMatch(version ->
                        version.coordinate().equals("com.example:lib-a") && version.version().equals("1.2.0")),
                () -> "api constraint must become a [bom.versions] pin: " + bom.versions());
        assertTrue(bom.versions().stream().anyMatch(version ->
                        version.coordinate().equals("com.example:lib-b") && version.version().equals("3.4.5")),
                () -> "runtime constraint must become a [bom.versions] pin: " + bom.versions());
        // A BOM carries no dependencies and no source scaffolding.
        assertTrue(config.dependencies().isEmpty());
        assertTrue(config.apiDependencies().isEmpty(),
                () -> "api platform()/plain deps must not land in [api.dependencies]: " + config.apiDependencies());
        // The allowDependencies() plain dep becomes a review note, not a dependency.
        assertTrue(draft.notes().stream().anyMatch(note ->
                        note.contains("org.postgresql:postgresql")
                                && note.contains("carries no dependencies")),
                () -> "plain dependency in a java-platform BOM needs a review note: " + draft.notes());
        assertTrue(draft.notes().stream().anyMatch(note -> note.contains("Drafted a [bom] member")),
                () -> "expected the drafted-bom review note: " + draft.notes());
    }

    @Test
    void kotlinJavaPlatformDraftsBomMember() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"acme-bom\"\n");
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                plugins {
                    `java-platform`
                }
                group = "com.acme.platform"
                version = "1.0.0"
                dependencies {
                    api(platform("com.fasterxml.jackson:jackson-bom:2.18.2"))
                    constraints {
                        api("com.example:lib-a:1.2.0")
                        runtime("com.example:lib-b:3.4.5")
                    }
                }
                """);

        ProjectConfig config = draft().config();

        assertEquals(PackageMode.BOM, config.packageSettings().mode());
        BomSettings bom = config.packageSettings().bom();
        assertTrue(bom.imports().stream().anyMatch(imported ->
                        imported.coordinate().equals("com.fasterxml.jackson:jackson-bom")
                                && imported.version().equals("2.18.2")),
                () -> "Kotlin-DSL platform() import must become a [bom.imports] entry: " + bom.imports());
        assertTrue(bom.versions().stream().anyMatch(version ->
                        version.coordinate().equals("com.example:lib-a") && version.version().equals("1.2.0")),
                () -> "Kotlin-DSL constraint must become a [bom.versions] pin: " + bom.versions());
        assertTrue(bom.versions().stream().anyMatch(version ->
                        version.coordinate().equals("com.example:lib-b") && version.version().equals("3.4.5")),
                () -> "Kotlin-DSL runtime constraint must become a [bom.versions] pin: " + bom.versions());
    }

    @Test
    void catalogConstraintReferenceResolvesIntoBomVersions() throws IOException {
        Files.createDirectories(tempDir.resolve("gradle"));
        Files.writeString(tempDir.resolve("gradle/libs.versions.toml"), """
                [libraries]
                lib-a = { module = "com.example:lib-a", version = "1.2.0" }
                """);
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'acme-bom'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins {
                    id 'java-platform'
                }
                group = 'com.acme.platform'
                version = '1.0.0'
                dependencies {
                    constraints {
                        api libs.lib.a
                    }
                }
                """);

        ProjectConfig config = draft().config();

        BomSettings bom = config.packageSettings().bom();
        assertTrue(bom.versions().stream().anyMatch(version ->
                        version.coordinate().equals("com.example:lib-a") && version.version().equals("1.2.0")),
                () -> "version-catalog constraint must resolve into [bom.versions]: " + bom.versions());
    }

    @Test
    void unparseableConstraintRaisesSignalAndIsNotSilentlyDropped() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'acme-bom'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins {
                    id 'java-platform'
                }
                group = 'com.acme.platform'
                version = '1.0.0'
                dependencies {
                    constraints {
                        api "com.example:lib-a:${someComputedVersion}"
                    }
                }
                """);

        var result = new GradleStaticProjectInspector().inspect(tempDir);
        DraftZoltToml draft = mapper.fromGradle(result);

        assertTrue(result.signals().stream().anyMatch(signal ->
                        signal.id().equals("gradle.dependency.dynamic-version")),
                () -> "an interpolated constraint must raise a signal rather than vanish: " + result.signals());
        assertTrue(draft.config().packageSettings().bom().versions().isEmpty(),
                () -> "the unresolved constraint must not be guessed into [bom.versions]: "
                        + draft.config().packageSettings().bom().versions());
    }

    private DraftZoltToml draft() throws IOException {
        return mapper.fromGradle(new GradleStaticProjectInspector().inspect(tempDir));
    }
}
