package com.zolt.classpath;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.resolve.ResolvedClasspathPackage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LockfileClasspathPackageConverterTest {
    private final ZoltLockfileReader reader = new ZoltLockfileReader();

    @TempDir
    private Path tempDir;

    @Test
    void ignoresNonJarArtifacts() {
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

        assertEquals(List.of(), LockfileClasspathPackageConverter.classpathPackages(lockfile));
    }

    @Test
    void reconstructsClasspathInputsFromLockfileData() throws IOException {
        List<ResolvedClasspathPackage> packages = LockfileClasspathPackageConverter.classpathPackages(
                reader.read(golden()));

        assertEquals(3, packages.size());
        ResolvedClasspathPackage guava = packages.stream()
                .filter(candidate -> candidate.resolvedPackage().packageId().equals(new PackageId("com.google.guava", "guava")))
                .findFirst()
                .orElseThrow();
        assertEquals(DependencyScope.COMPILE, guava.scope());
        assertEquals("guava-33.4.0-jre.jar", guava.resolvedPackage().jarPath().getFileName().toString());
        assertEquals("guava-33.4.0-jre.pom", guava.resolvedPackage().pomPath().getFileName().toString());
    }

    @Test
    void reconstructsClasspathInputsUnderCacheRoot() {
        List<ResolvedClasspathPackage> packages = LockfileClasspathPackageConverter.classpathPackages(reader.read("""
                version = 1

                [[package]]
                id = "com.google.guava:guava"
                version = "33.4.0-jre"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar"
                pom = "com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom"
                dependencies = []
                """), Path.of("cache"));

        ResolvedClasspathPackage guava = packages.stream()
                .filter(candidate -> candidate.resolvedPackage().packageId().equals(new PackageId("com.google.guava", "guava")))
                .findFirst()
                .orElseThrow();

        assertEquals(
                Path.of("cache/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar"),
                guava.resolvedPackage().jarPath());
    }

    @Test
    void verifiesArtifactIntegrityBeforeReconstructingCachedClasspathInputs() throws IOException {
        Path cacheRoot = tempDir.resolve("cache");
        Path jar = write(cacheRoot.resolve("com/example/demo/1.0.0/demo-1.0.0.jar"), "actual jar bytes");
        ZoltLockfile lockfile = reader.read("""
                version = 1

                [[package]]
                id = "com.example:demo"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "com/example/demo/1.0.0/demo-1.0.0.jar"
                jarSha256 = "%s"
                dependencies = []
                """.formatted(sha256("expected jar bytes")));

        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot));

        assertTrue(exception.getMessage().contains("Cached jar integrity check failed for com.example:demo:1.0.0"));
        assertTrue(exception.getMessage().contains("but found " + sha256(jar)));
    }

    @Test
    void reconstructsWorkspaceClasspathInputsUnderWorkspaceRoot() throws IOException {
        Path workspaceRoot = tempDir.resolve("workspace");
        Files.createDirectories(workspaceRoot.resolve("modules/core"));
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

        List<ResolvedClasspathPackage> packages = LockfileClasspathPackageConverter.classpathPackages(
                lockfile,
                tempDir.resolve("cache"),
                workspaceRoot);

        assertEquals(
                workspaceRoot.resolve("modules/core/target/classes"),
                packages.getFirst().resolvedPackage().jarPath());
    }

    @Test
    void rejectsWorkspaceClasspathMemberThatEscapesWorkspaceRoot() {
        ZoltLockfile lockfile = reader.read("""
                version = 1

                [[package]]
                id = "com.acme:core"
                version = "0.1.0"
                source = "workspace"
                scope = "compile"
                direct = true
                workspace = "../outside"
                workspaceOutput = "target/classes"
                dependencies = []
                """);

        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> LockfileClasspathPackageConverter.classpathPackages(
                        lockfile,
                        tempDir.resolve("cache"),
                        tempDir.resolve("workspace")));

        assertTrue(exception.getMessage().contains("workspace"));
        assertTrue(exception.getMessage().contains("../outside"));
    }

    @Test
    void rejectsWorkspaceClasspathOutputThatEscapesMemberRoot() throws IOException {
        Path workspaceRoot = tempDir.resolve("workspace");
        Files.createDirectories(workspaceRoot.resolve("modules/core"));
        ZoltLockfile lockfile = reader.read("""
                version = 1

                [[package]]
                id = "com.acme:core"
                version = "0.1.0"
                source = "workspace"
                scope = "compile"
                direct = true
                workspace = "modules/core"
                workspaceOutput = "../classes"
                dependencies = []
                """);

        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> LockfileClasspathPackageConverter.classpathPackages(
                        lockfile,
                        tempDir.resolve("cache"),
                        workspaceRoot));

        assertTrue(exception.getMessage().contains("workspaceOutput"));
        assertTrue(exception.getMessage().contains("../classes"));
    }

    private static String golden() throws IOException {
        return new String(
                LockfileClasspathPackageConverterTest.class
                        .getResourceAsStream("/golden/zolt-lock-writer.golden")
                        .readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static Path write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static String sha256(Path path) throws IOException {
        return sha256(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
