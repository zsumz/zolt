package com.zolt.build;

import static com.zolt.build.PackageServiceTestSupport.buildSettingsWithMetadata;
import static com.zolt.build.PackageServiceTestSupport.config;
import static com.zolt.build.PackageServiceTestSupport.readEntry;
import static com.zolt.build.PackageServiceTestSupport.source;
import static com.zolt.build.PackageServiceTestSupport.writeLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageServiceAuxiliaryArtifactsTest {
    private final PackageService packageService = new PackageService();

    @TempDir
    private Path projectDir;

    @Test
    void packagesRequestedLibraryArtifacts() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Calculator.java", """
                package com.example;

                /** Adds numbers. */
                public final class Calculator {
                    /** Adds two integers. */
                    public int add(int left, int right) {
                        return left + right;
                    }
                }
                """);
        source(projectDir, "src/test/java/com/example/CalculatorTest.java", """
                package com.example;

                public final class CalculatorTest {
                }
                """);
        source(projectDir, "src/main/openapi/api.yaml", "openapi: 3.1.0\n");
        source(projectDir, "target/generated/sources/openapi/com/example/generated/GeneratedApi.java", """
                package com.example.generated;

                public interface GeneratedApi {
                }
                """);
        Files.createDirectories(projectDir.resolve("target/test-classes/com/example"));
        Files.write(projectDir.resolve("target/test-classes/com/example/CalculatorTest.class"), new byte[] {0});
        BuildSettings build = BuildSettings.defaults()
                .withGeneratedSources(
                        List.of(new GeneratedSourceStep(
                                "openapi",
                                GeneratedSourceKind.DECLARED_ROOT,
                                "java",
                                "target/generated/sources/openapi",
                                List.of("src/main/openapi/api.yaml"),
                                true,
                                false)),
                        List.of());
        ProjectConfig config = config(Optional.empty())
                .withBuildSettings(build)
                .withPackageSettings(new PackageSettings(PackageMode.THIN, true, true, true, null));

        PackageResult result = packageService.packageJar(projectDir, config, projectDir.resolve("cache"));

        assertEquals(List.of("sources", "javadoc", "tests"), result.artifacts().stream()
                .map(PackageArtifact::classifier)
                .toList());
        try (JarFile sources = new JarFile(projectDir.resolve("target/demo-0.1.0-sources.jar").toFile())) {
            assertNotNull(sources.getEntry("com/example/Calculator.java"));
            assertNull(sources.getEntry("com/example/generated/GeneratedApi.java"));
        }
        try (JarFile javadocs = new JarFile(projectDir.resolve("target/demo-0.1.0-javadoc.jar").toFile())) {
            assertTrue(javadocs.stream().anyMatch(entry -> entry.getName().endsWith("Calculator.html")));
        }
        try (JarFile tests = new JarFile(projectDir.resolve("target/demo-0.1.0-tests.jar").toFile())) {
            assertNotNull(tests.getEntry("com/example/CalculatorTest.class"));
        }
    }

    @Test
    void packagesSupplementalArtifactsUnderOutputRoot() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Calculator.java", """
                package com.example;

                /** Adds numbers. */
                public final class Calculator {
                    /** Adds two integers. */
                    public int add(int left, int right) {
                        return left + right;
                    }
                }
                """);
        source(projectDir, "src/test/java/com/example/CalculatorTest.java", """
                package com.example;

                public final class CalculatorTest {
                }
                """);
        Files.createDirectories(projectDir.resolve(".zolt/build/test-classes/com/example"));
        Files.write(projectDir.resolve(".zolt/build/test-classes/com/example/CalculatorTest.class"), new byte[] {0});
        BuildSettings build = new BuildSettings(
                "src/main/java",
                "src/test/java",
                ".zolt/build",
                ".zolt/build/classes",
                ".zolt/build/test-classes");
        ProjectConfig config = config(Optional.empty())
                .withBuildSettings(build)
                .withPackageSettings(new PackageSettings(PackageMode.THIN, true, true, true, null));

        PackageResult result = packageService.packageJar(projectDir, config, projectDir.resolve("cache"));

        assertEquals(projectDir.resolve(".zolt/build/demo-0.1.0-sources.jar"), result.artifacts().get(0).path());
        assertEquals(projectDir.resolve(".zolt/build/demo-0.1.0-javadoc.jar"), result.artifacts().get(1).path());
        assertEquals(projectDir.resolve(".zolt/build/demo-0.1.0-tests.jar"), result.artifacts().get(2).path());
        assertTrue(Files.exists(projectDir.resolve(".zolt/build/javadoc")));
        assertFalse(Files.exists(projectDir.resolve("target/demo-0.1.0-sources.jar")));
    }

    @Test
    void packagesGeneratedBuildInfoMetadata() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withBuildSettings(buildSettingsWithMetadata(new BuildMetadataSettings(true, false, true)));

        PackageResult result = packageService.packageJar(projectDir, config, projectDir.resolve("cache"));

        assertEquals(2, result.entryCount());
        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            assertEquals("""
                    build.artifact=demo
                    build.group=com.example
                    build.name=demo
                    build.time=1970-01-01T00:00:00Z
                    build.version=0.1.0
                    """, readEntry(jar, "META-INF/build-info.properties"));
        }
    }

    @Test
    void packagesJavadocsWhenProjectDirectoryIsRelative() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Calculator.java", """
                package com.example;

                /** Adds numbers. */
                public final class Calculator {
                    /** Adds two integers. */
                    public int add(int left, int right) {
                        return left + right;
                    }
                }
                """);
        Path classFile = projectDir.resolve("target/classes/com/example/Calculator.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[] {0});
        ProjectConfig config = config(Optional.empty())
                .withPackageSettings(new PackageSettings(PackageMode.THIN, false, true, false, null));
        Path relativeProjectDir = Path.of("")
                .toAbsolutePath()
                .normalize()
                .relativize(projectDir.toAbsolutePath().normalize());
        BuildResult buildResult = new BuildResult(
                Optional.empty(),
                1,
                0,
                relativeProjectDir.resolve("target/classes"),
                "",
                false);

        PackageResult result = packageService.packageJar(relativeProjectDir, config, buildResult, projectDir.resolve("cache"));

        assertEquals(projectDir.resolve("target/demo-0.1.0.jar"), result.jarPath());
        assertEquals(List.of("javadoc"), result.artifacts().stream()
                .map(PackageArtifact::classifier)
                .toList());
        try (JarFile javadocs = new JarFile(projectDir.resolve("target/demo-0.1.0-javadoc.jar").toFile())) {
            assertTrue(javadocs.stream().anyMatch(entry -> entry.getName().endsWith("Calculator.html")));
        }
    }

    @Test
    void javadocFailuresPreserveToolOutputAndNextStep() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Calculator.java", """
                package com.example;

                public final class Calculator {
                    /**
                     * Adds numbers.
                     * @param missing this parameter does not exist
                     */
                    public int add() {
                        return 1;
                    }
                }
                """);
        ProjectConfig config = config(Optional.empty())
                .withPackageSettings(new PackageSettings(PackageMode.THIN, false, true, false, null));

        PackageException exception = assertThrows(
                PackageException.class,
                () -> packageService.packageJar(projectDir, config, projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("javadoc failed with exit code"));
        assertTrue(exception.getMessage().contains("disable [package].javadoc"));
        assertTrue(exception.getMessage().contains("missing"));
    }
}
