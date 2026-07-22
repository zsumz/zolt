package sh.zolt.cli.resolve;

import static sh.zolt.cli.CliTestSupport.execute;
import static sh.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end CLI coverage for the deferred-SNAPSHOT policy: a directly declared {@code -SNAPSHOT}
 * dependency now parses in {@code zolt.toml} and the resolve-time {@code SnapshotAllowance} decides it —
 * resolving from a maven-local overlay when present, and otherwise failing with actionable guidance.
 */
final class ResolveCommandSnapshotTest {
    @TempDir
    private Path tempDir;

    @Test
    void directSnapshotResolvesFromOverlay() throws IOException {
        Path projectDir = tempDir.resolve("snap-project");
        Path overlay = tempDir.resolve("m2/repository");
        writeLocalArtifact(overlay, "com.example", "snap", "1.0.0-SNAPSHOT");
        writeSnapshotProject(projectDir);

        CommandResult result = execute(
                "resolve",
                "--offline",
                "--cwd", projectDir.toString(),
                "--repository-overlay", "maven-local",
                "--maven-local-root", overlay.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode(), result.stderr());
        Path lockfile = projectDir.resolve("zolt.lock");
        assertTrue(Files.exists(lockfile));
        String lock = Files.readString(lockfile);
        assertTrue(lock.contains("1.0.0-SNAPSHOT"), lock);
        assertTrue(lock.contains("local-overlay:maven-local"), lock);
    }

    @Test
    void directSnapshotWithoutOverlayFailsWithActionableError() throws IOException {
        Path projectDir = tempDir.resolve("snap-no-overlay");
        writeSnapshotProject(projectDir);

        CommandResult result = execute(
                "resolve",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        String stderr = result.stderr();
        assertTrue(stderr.contains("Unsupported SNAPSHOT dependency version `1.0.0-SNAPSHOT`"), stderr);
        assertTrue(stderr.contains("com.example:snap"), stderr);
        assertTrue(stderr.contains("dependencies"), stderr);
        assertTrue(stderr.contains("workspace members and maven-local overlay artifacts"), stderr);
        assertTrue(stderr.contains("remote SNAPSHOT feeds are unsupported by design"), stderr);
        assertTrue(stderr.contains("Next: Install"), stderr);
        assertTrue(stderr.contains("--repository-overlay maven-local"), stderr);
    }

    @Test
    void lockedResolveSurfacesActionableSnapshotRejection() throws IOException {
        Path projectDir = tempDir.resolve("snap-locked");
        Path overlay = tempDir.resolve("m2/repository");
        writeLocalArtifact(overlay, "com.example", "snap", "1.0.0-SNAPSHOT");
        writeSnapshotProject(projectDir);

        // Seed a valid lockfile via the overlay.
        CommandResult seeded = execute(
                "resolve",
                "--offline",
                "--cwd", projectDir.toString(),
                "--repository-overlay", "maven-local",
                "--maven-local-root", overlay.toString(),
                "--cache-root", tempDir.resolve("cache").toString());
        assertEquals(0, seeded.exitCode(), seeded.stderr());

        // A locked re-resolve without the overlay can no longer satisfy the SNAPSHOT; the actionable
        // rejection must surface rather than a lockfile-drift message.
        CommandResult locked = execute(
                "resolve",
                "--locked",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, locked.exitCode());
        assertTrue(locked.stderr().contains("Unsupported SNAPSHOT dependency version `1.0.0-SNAPSHOT`"),
                locked.stderr());
        assertTrue(locked.stderr().contains("Next: Install"), locked.stderr());
    }

    @Test
    void directSnapshotOverlayResolutionIsDeterministic() throws IOException {
        Path projectDir = tempDir.resolve("snap-determinism");
        Path overlay = tempDir.resolve("m2/repository");
        writeLocalArtifact(overlay, "com.example", "snap", "1.0.0-SNAPSHOT");
        writeSnapshotProject(projectDir);

        execute(
                "resolve",
                "--offline",
                "--cwd", projectDir.toString(),
                "--repository-overlay", "maven-local",
                "--maven-local-root", overlay.toString(),
                "--cache-root", tempDir.resolve("cache-a").toString());
        String first = Files.readString(projectDir.resolve("zolt.lock"));

        execute(
                "resolve",
                "--offline",
                "--cwd", projectDir.toString(),
                "--repository-overlay", "maven-local",
                "--maven-local-root", overlay.toString(),
                "--cache-root", tempDir.resolve("cache-b").toString());
        String second = Files.readString(projectDir.resolve("zolt.lock"));

        assertEquals(first, second);
    }

    private static void writeSnapshotProject(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("snap-demo") + """

                [repositories]
                central = "https://repo.maven.apache.org/maven2"

                [dependencies]
                "com.example:snap" = "1.0.0-SNAPSHOT"
                """);
    }

    private static void writeLocalArtifact(Path root, String group, String artifact, String version) {
        String base = group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version;
        String pom = """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(group, artifact, version);
        writeFile(root.resolve(base + ".pom"), pom.getBytes(StandardCharsets.UTF_8));
        writeFile(root.resolve(base + ".jar"), emptyJar());
    }

    private static byte[] emptyJar() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (JarOutputStream jar = new JarOutputStream(bytes)) {
                jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
                jar.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not build fixture jar bytes.", exception);
        }
    }

    private static void writeFile(Path path, byte[] content) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write fixture artifact " + path + ".", exception);
        }
    }
}
