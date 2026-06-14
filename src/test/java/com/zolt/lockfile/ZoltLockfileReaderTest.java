package com.zolt.lockfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.PackageId;
import com.zolt.resolve.ConflictSelectionReason;
import com.zolt.resolve.DependencyScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ZoltLockfileReaderTest {
    private final ZoltLockfileReader reader = new ZoltLockfileReader();

    @Test
    void readsCurrentLockfileVersion() throws IOException {
        ZoltLockfile lockfile = reader.read(golden());

        assertEquals(ZoltLockfile.CURRENT_VERSION, lockfile.version());
        assertEquals("sha256:project-inputs", lockfile.projectResolutionFingerprint().orElseThrow());
        assertEquals(
                List.of("dependencies.compile=sha256:compile-inputs", "repositories=sha256:repo-inputs"),
                lockfile.projectResolutionInputFingerprints());
        assertEquals(3, lockfile.packages().size());
        assertEquals(1, lockfile.conflicts().size());
    }

    @Test
    void readsLegacyCurrentVersionWithoutResolutionMetadata() {
        ZoltLockfile lockfile = reader.read("version = 1\n");

        assertTrue(lockfile.projectResolutionFingerprint().isEmpty());
        assertEquals(List.of(), lockfile.projectResolutionInputFingerprints());
    }

    @Test
    void readsAliasFingerprint() {
        ZoltLockfile lockfile = reader.read("""
                version = 1
                aliasFingerprint = "sha256:alias-inputs"
                """);

        assertEquals("sha256:alias-inputs", lockfile.aliasFingerprint().orElseThrow());
    }

    @Test
    void readsProjectResolutionFingerprint() {
        ZoltLockfile lockfile = reader.read("""
                version = 1
                projectResolutionFingerprint = "sha256:project-inputs"
                projectResolutionInputFingerprints = ["repositories=sha256:repo-inputs", "dependencies.compile=sha256:compile-inputs"]
                """);

        assertEquals("sha256:project-inputs", lockfile.projectResolutionFingerprint().orElseThrow());
        assertEquals(
                List.of("repositories=sha256:repo-inputs", "dependencies.compile=sha256:compile-inputs"),
                lockfile.projectResolutionInputFingerprints());
    }

    @Test
    void readsPackageFields() throws IOException {
        ZoltLockfile lockfile = reader.read(golden());
        LockPackage guava = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.google.guava", "guava")))
                .findFirst()
                .orElseThrow();

        assertEquals("33.4.0-jre", guava.version());
        assertEquals("maven-central", guava.source());
        assertEquals(DependencyScope.COMPILE, guava.scope());
        assertTrue(guava.direct());
        assertEquals("jar-checksum", guava.jarSha256().orElseThrow());
        assertEquals(List.of(
                "com.google.guava:failureaccess:1.0.2",
                "org.slf4j:slf4j-api:2.0.16"), guava.dependencies());
    }

    @Test
    void readsConflictFields() throws IOException {
        LockConflict conflict = reader.read(golden()).conflicts().getFirst();

        assertEquals(new PackageId("org.slf4j", "slf4j-api"), conflict.packageId());
        assertEquals("2.0.16", conflict.selectedVersion());
        assertEquals(List.of("1.7.36", "2.0.16"), conflict.requestedVersions());
        assertEquals(ConflictSelectionReason.DIRECT_DEPENDENCY, conflict.reason());
    }

    @Test
    void readsPolicyEffects() {
        ZoltLockfile lockfile = reader.read("""
                version = 1

                [[policy]]
                kind = "global-exclusion"
                id = "commons-logging:commons-logging"
                requested = "1.2"
                source = "org.springframework.boot:spring-boot-starter-web:3.3.6"
                policy = "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)"
                """);

        LockPolicyEffect effect = lockfile.policyEffects().getFirst();
        assertEquals("global-exclusion", effect.kind());
        assertEquals(new PackageId("commons-logging", "commons-logging"), effect.packageId());
        assertEquals("1.2", effect.requestedVersion().orElseThrow());
        assertEquals("org.springframework.boot:spring-boot-starter-web:3.3.6", effect.source().orElseThrow());
        assertEquals("[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)", effect.policy());
    }

    @Test
    void readsInternalToolingScopes() {
        ZoltLockfile lockfile = reader.read("""
                version = 1

                [[package]]
                id = "com.example:processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "processor"
                direct = true
                dependencies = []

                [[package]]
                id = "com.example:test-processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "test-processor"
                direct = true
                dependencies = []

                [[package]]
                id = "io.quarkus:quarkus-rest-deployment"
                version = "3.33.0"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                dependencies = []

                [[package]]
                id = "org.jacoco:org.jacoco.cli"
                version = "0.8.14"
                source = "maven-central"
                scope = "tool-coverage"
                direct = false
                dependencies = []
                """);

        assertEquals(DependencyScope.PROCESSOR, lockfile.packages().get(0).scope());
        assertEquals(DependencyScope.TEST_PROCESSOR, lockfile.packages().get(1).scope());
        assertEquals(DependencyScope.QUARKUS_DEPLOYMENT, lockfile.packages().get(2).scope());
        assertEquals(DependencyScope.TOOL_COVERAGE, lockfile.packages().get(3).scope());
    }

    @Test
    void readsWorkspacePackageFields() {
        ZoltLockfile lockfile = reader.read("""
                version = 1

                [[package]]
                id = "com.acme:core"
                version = "0.1.0"
                source = "workspace"
                scope = "compile"
                direct = true
                workspace = "modules/core"
                workspaceOutput = "target/classes"
                dependencies = []
                """);

        LockPackage lockPackage = lockfile.packages().getFirst();
        assertEquals("workspace", lockPackage.source());
        assertEquals("modules/core", lockPackage.workspace().orElseThrow());
        assertEquals("target/classes", lockPackage.workspaceOutput().orElseThrow());
        assertEquals(List.of(), lockPackage.members());
    }

    @Test
    void readsOptionalPackageMembers() {
        ZoltLockfile lockfile = reader.read("""
                version = 1

                [[package]]
                id = "com.example:demo"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                members = ["apps/api", "modules/core"]
                dependencies = []
                """);

        assertEquals(List.of("apps/api", "modules/core"), lockfile.packages().getFirst().members());
    }

    @Test
    void readsOptionalExportedByMembers() {
        ZoltLockfile lockfile = reader.read("""
                version = 1

                [[package]]
                id = "com.example:contract"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                exportedBy = ["modules/api"]
                dependencies = []
                """);

        assertEquals(List.of("modules/api"), lockfile.packages().getFirst().exportedBy());
    }

    @Test
    void readsNonJarArtifactFields() {
        ZoltLockfile lockfile = reader.read("""
                version = 1

                [[package]]
                id = "io.quarkus.platform:quarkus-bom-quarkus-platform-properties"
                version = "3.33.0"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                pom = "io/quarkus/platform/quarkus-bom-quarkus-platform-properties/3.33.0/quarkus-bom-quarkus-platform-properties-3.33.0.pom"
                pomSha256 = "pom-checksum"
                artifact = "io/quarkus/platform/quarkus-bom-quarkus-platform-properties/3.33.0/quarkus-bom-quarkus-platform-properties-3.33.0.properties"
                artifactType = "properties"
                artifactSha256 = "properties-checksum"
                dependencies = []
                """);

        LockPackage lockPackage = lockfile.packages().getFirst();

        assertTrue(lockPackage.jar().isEmpty());
        assertEquals("properties", lockPackage.artifactType().orElseThrow());
        assertEquals(
                "io/quarkus/platform/quarkus-bom-quarkus-platform-properties/3.33.0/quarkus-bom-quarkus-platform-properties-3.33.0.properties",
                lockPackage.artifact().orElseThrow());
        assertEquals("properties-checksum", lockPackage.artifactSha256().orElseThrow());
    }

    @Test
    void rejectsUnsupportedLockfileVersion() {
        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> reader.read("version = 99\n"));

        assertEquals(
                "Unsupported zolt.lock version 99. Run `zolt resolve` with a compatible Zolt version to regenerate the lockfile.",
                exception.getMessage());
    }

    @Test
    void rejectsCorruptTomlWithActionableError() {
        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> reader.read("version =\n"));

        assertTrue(exception.getMessage().contains("Could not parse zolt.lock."));
        assertTrue(exception.getMessage().contains("Fix the TOML syntax"));
    }

    @Test
    void rejectsMissingRequiredPackageField() {
        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> reader.read("""
                        version = 1

                        [[package]]
                        id = "com.example:demo"
                        version = "1.0.0"
                        scope = "compile"
                        direct = true
                        dependencies = []
                        """));

        assertEquals("Missing required string field `source` in zolt.lock.", exception.getMessage());
    }

    private static String golden() throws IOException {
        return new String(
                ZoltLockfileReaderTest.class.getResourceAsStream("/golden/zolt-lock-writer.golden").readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
