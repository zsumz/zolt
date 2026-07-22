package sh.zolt.cli.command.insight;

import static sh.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.command.insight.ZoltResolutionLoader.ZoltResolution;
import sh.zolt.explain.verify.VerifyScope;
import sh.zolt.resolve.ResolveService;
import sh.zolt.resolve.materialization.RepositoryOverlay;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code zolt explain verify} threads its {@code --repository-overlay} option into the Zolt-side
 * resolution, so a project whose dependency materializes only from a maven-local overlay can be
 * verified. Without the overlay the same offline resolution fails, proving the artifact comes solely
 * from the overlay.
 */
final class ZoltResolutionOverlayVerifyTest {
    @TempDir
    private Path tempDir;

    @Test
    void overlayPassthroughResolvesDependencyMaterializedOnlyFromOverlay() throws IOException {
        Path projectDir = tempDir.resolve("proj");
        Path overlay = tempDir.resolve("m2/repository");
        writeLocalArtifact(overlay, "com.example", "overlay-only", "1.0.0");
        writeProject(projectDir);

        ZoltResolution zolt = new ZoltResolutionLoader(new ResolveService())
                .load(projectDir, tempDir.resolve("cache"), true, List.of(RepositoryOverlay.mavenLocal(overlay)));

        List<String> compileCoordinates = zolt.modules().stream()
                .flatMap(module -> module.artifacts(VerifyScope.COMPILE).stream())
                .map(artifact -> artifact.coordinate())
                .toList();
        assertTrue(compileCoordinates.contains("com.example:overlay-only:1.0.0"), compileCoordinates.toString());
    }

    @Test
    void withoutOverlayTheSameOfflineResolutionCannotFindTheDependency() throws IOException {
        Path projectDir = tempDir.resolve("proj-no-overlay");
        writeProject(projectDir);

        ZoltResolutionLoader loader = new ZoltResolutionLoader(new ResolveService());
        // No overlay and an empty offline cache: the overlay-only artifact cannot be materialized.
        assertThrows(RuntimeException.class,
                () -> loader.load(projectDir, tempDir.resolve("cache-empty"), true, List.of()));
    }

    private static void writeProject(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("proj") + """

                [repositories]
                central = "https://repo.maven.apache.org/maven2"

                [dependencies]
                "com.example:overlay-only" = "1.0.0"
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
