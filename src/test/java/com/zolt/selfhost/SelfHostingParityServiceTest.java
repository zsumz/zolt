package com.zolt.selfhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.BuildResult;
import com.zolt.build.PackageResult;
import com.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SelfHostingParityServiceTest {
    @TempDir
    private Path tempDir;

    @Test
    void comparesJarEntriesIgnoringManifest() throws IOException {
        writeProject();
        Path bootstrapJar = tempDir.resolve("build/modules/demo.jar");
        Path zoltJar = tempDir.resolve("target/demo-0.1.0.jar");
        writeJar(bootstrapJar, List.of("META-INF/MANIFEST.MF", "com/example/Main.class", "config/app.properties"));
        writeJar(zoltJar, List.of("META-INF/MANIFEST.MF", "com/example/Main.class", "config/app.properties"));
        SelfHostingParityService service = service(zoltJar);

        SelfHostingParityResult result = service.compare(tempDir, tempDir.resolve("cache"), bootstrapJar);

        assertTrue(result.ok());
        assertEquals(bootstrapJar, result.bootstrapJar());
        assertEquals(zoltJar, result.zoltJar());
        assertEquals(Set.of(), result.missingFromZolt());
        assertEquals(Set.of(), result.extraInZolt());
    }

    @Test
    void reportsMissingAndExtraEntries() throws IOException {
        writeProject();
        Path bootstrapJar = tempDir.resolve("build/modules/demo.jar");
        Path zoltJar = tempDir.resolve("target/demo-0.1.0.jar");
        writeJar(bootstrapJar, List.of("com/example/Main.class", "config/expected.properties"));
        writeJar(zoltJar, List.of("com/example/Main.class", "config/extra.properties"));
        SelfHostingParityService service = service(zoltJar);

        SelfHostingParityResult result = service.compare(tempDir, tempDir.resolve("cache"), bootstrapJar);

        assertFalse(result.ok());
        assertEquals(Set.of("config/expected.properties"), result.missingFromZolt());
        assertEquals(Set.of("config/extra.properties"), result.extraInZolt());
    }

    @Test
    void missingBootstrapJarIsActionable() throws IOException {
        writeProject();
        SelfHostingParityService service = service(tempDir.resolve("target/demo-0.1.0.jar"));

        SelfHostingParityException exception = assertThrows(
                SelfHostingParityException.class,
                () -> service.compare(tempDir, tempDir.resolve("cache"), Path.of("missing.jar")));

        assertTrue(exception.getMessage().contains("Self-hosting parity requires bootstrap jar"));
        assertTrue(exception.getMessage().contains("--bootstrap-jar"));
    }

    private SelfHostingParityService service(Path zoltJar) {
        return new SelfHostingParityService(
                new ZoltTomlParser(),
                (projectDirectory, config, cacheRoot) -> new PackageResult(
                        new BuildResult(
                                Optional.empty(),
                                1,
                                0,
                                projectDirectory.resolve("target/classes"),
                                ""),
                        zoltJar,
                        1,
                        true));
    }

    private void writeProject() throws IOException {
        Files.writeString(tempDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                main = "com.example.Main"

                [repositories]
                central = "https://repo.maven.apache.org/maven2"
                """);
    }

    private static void writeJar(Path path, List<String> entries) throws IOException {
        Files.createDirectories(path.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            for (String entry : entries) {
                output.putNextEntry(new JarEntry(entry));
                output.write(new byte[] {0});
                output.closeEntry();
            }
        }
    }
}
