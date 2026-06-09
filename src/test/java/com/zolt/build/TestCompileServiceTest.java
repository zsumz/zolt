package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.resolve.Classpath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestCompileServiceTest {
    private final TestCompileService testCompileService = new TestCompileService();

    @TempDir
    private Path projectDir;

    @Test
    void compilesMainSourcesBeforeTestSources() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "hello";
                    }
                }
                """);
        source("src/test/java/com/example/MainTest.java", """
                package com.example;

                public final class MainTest {
                    public String message() {
                        return Main.message();
                    }
                }
                """);

        TestCompileResult result = testCompileService.compileTests(
                projectDir,
                config(),
                projectDir.resolve("cache"));

        assertEquals(1, result.buildResult().sourceCount());
        assertEquals(1, result.sourceCount());
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/MainTest.class")));
    }

    @Test
    void copiesMainAndTestResourcesDuringTestCompilation() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        source("src/main/resources/META-INF/native-image/reflect-config.json", "[]\n");
        source("src/test/resources/fixtures/input.txt", "fixture\n");

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertEquals(1, result.buildResult().resourceCount());
        assertEquals(1, result.resourceCount());
        assertEquals("[]\n", Files.readString(projectDir.resolve("target/classes/META-INF/native-image/reflect-config.json")));
        assertEquals("fixture\n", Files.readString(projectDir.resolve("target/test-classes/fixtures/input.txt")));
    }

    @Test
    void testCompileClasspathIncludesTestDependencies() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path helperJar = cacheRoot.resolve("com/example/helper/1.0.0/helper-1.0.0.jar");
        createHelperJar(helperJar);
        writeLockfile("""
                version = 1

                [[package]]
                id = "com.example:helper"
                version = "1.0.0"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "com/example/helper/1.0.0/helper-1.0.0.jar"
                dependencies = []
                """);
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", """
                package com.example;

                import com.example.helper.Helper;

                public final class MainTest {
                    public String message() {
                        return Helper.message();
                    }
                }
                """);

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), cacheRoot);

        assertEquals(1, result.sourceCount());
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/MainTest.class")));
    }

    @Test
    void testCompilationUsesTestProcessorClasspathAndGeneratedSources() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path processorJar = cacheRoot.resolve("com/example/test-processor/1.0.0/test-processor-1.0.0.jar");
        Files.createDirectories(processorJar.getParent());
        Files.copy(
                AnnotationProcessorFixture.processorJar(projectDir.resolve("processor-work")),
                processorJar,
                StandardCopyOption.REPLACE_EXISTING);
        writeLockfile("""
                version = 1

                [[package]]
                id = "com.example:test-processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "test-processor"
                direct = true
                jar = "com/example/test-processor/1.0.0/test-processor-1.0.0.jar"
                dependencies = []
                """);
        source("src/test/java/com/example/MainTest.java", """
                package com.example;

                public final class MainTest {
                    public String message() {
                        return GeneratedMessage.value();
                    }
                }
                """);

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), cacheRoot);

        assertEquals(0, result.buildResult().sourceCount());
        assertEquals(1, result.sourceCount());
        assertTrue(Files.exists(projectDir.resolve(
                "target/generated/test-sources/annotations/com/example/GeneratedMessage.java")));
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/MainTest.class")));
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/GeneratedMessage.class")));
    }

    @Test
    void repeatedTestCompilationSkipsJavacWhenInputsAreCurrent() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");

        TestCompileResult first = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        TestCompileResult second = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertFalse(first.testCompilationSkipped());
        assertFalse(first.buildResult().mainCompilationSkipped());
        assertTrue(second.buildResult().mainCompilationSkipped());
        assertTrue(second.testCompilationSkipped());
        assertEquals(1, second.sourceCount());
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/MainTest.class")));
    }

    @Test
    void testSourceChangeInvalidatesTestCompileFingerprint() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        Path testSource = source(
                "src/test/java/com/example/MainTest.java",
                "package com.example; public final class MainTest { public String message() { return \"one\"; } }\n");
        testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(
                testSource,
                "package com.example; public final class MainTest { public String message() { return \"two\"; } }\n");

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertTrue(result.buildResult().mainCompilationSkipped());
        assertFalse(result.testCompilationSkipped());
    }

    @Test
    void mainOutputChangeInvalidatesTestCompileFingerprint() throws IOException {
        writeLockfile("version = 1\n");
        Path mainSource = source(
                "src/main/java/com/example/Main.java",
                "package com.example; public final class Main { public static String message() { return \"one\"; } }\n");
        source("src/test/java/com/example/MainTest.java", """
                package com.example;

                public final class MainTest {
                    public String message() {
                        return Main.message();
                    }
                }
                """);
        testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(
                mainSource,
                "package com.example; public final class Main { public static String message() { return \"two\"; } }\n");

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertFalse(result.buildResult().mainCompilationSkipped());
        assertFalse(result.testCompilationSkipped());
    }

    @Test
    void missingExpectedTestClassPreventsTestCompileSkip() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        Files.delete(projectDir.resolve("target/test-classes/com/example/MainTest.class"));

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertTrue(result.buildResult().mainCompilationSkipped());
        assertFalse(result.testCompilationSkipped());
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/MainTest.class")));
    }

    @Test
    void testCompilerErrorsAreSurfacedClearly() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/BrokenTest.java", """
                package com.example;

                public final class BrokenTest {
                    missing
                }
                """);

        JavacException exception = assertThrows(
                JavacException.class,
                () -> testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("javac failed with exit code"));
        assertTrue(exception.getMessage().contains("BrokenTest.java"));
    }

    private static ProjectConfig config() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", currentJavaMajorVersion(), Optional.of("com.example.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }

    private Path source(String path, String content) throws IOException {
        Path source = projectDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
        return source;
    }

    private void writeLockfile(String content) throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), content);
    }

    private void createHelperJar(Path jar) throws IOException {
        Path helperSource = projectDir.resolve("helper-src/com/example/helper/Helper.java");
        Files.createDirectories(helperSource.getParent());
        Files.writeString(helperSource, """
                package com.example.helper;

                public final class Helper {
                    public static String message() {
                        return "helper";
                    }
                }
                """);
        Path helperClasses = projectDir.resolve("helper-classes");
        new JavacRunner().compile(
                currentJavac(),
                List.of(helperSource),
                new Classpath(List.of()),
                helperClasses);

        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            JarEntry entry = new JarEntry("com/example/helper/Helper.class");
            output.putNextEntry(entry);
            output.write(Files.readAllBytes(helperClasses.resolve("com/example/helper/Helper.class")));
            output.closeEntry();
        }
    }

    private static Path currentJavac() {
        return Path.of(System.getProperty("java.home")).resolve("bin").resolve(executable("javac"));
    }

    private static String executable(String name) {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? name + ".exe"
                : name;
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
