package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.classpath.Classpath;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
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
    void compileTestsWithClasspathsReturnsReusedClasspaths() throws IOException {
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
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");

        TestCompileResultWithClasspaths result =
                testCompileService.compileTestsWithClasspaths(projectDir, config(), cacheRoot);

        assertEquals(1, result.testCompileResult().sourceCount());
        assertEquals(List.of(helperJar), result.classpaths().test().entries());
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

    @Test
    void compilesGroovyTestSourcesAfterJavaTestSources() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "main";
                    }
                }
                """);
        source("src/test/java/com/example/TestHelper.java", """
                package com.example;

                public final class TestHelper {
                    public static String message() {
                        return Main.message();
                    }
                }
                """);
        Path groovySource = source("src/test/groovy/com/example/MainSpec.groovy", """
                package com.example

                final class MainSpec {
                    String message() {
                        return TestHelper.message()
                    }
                }
                """);
        List<List<String>> groovyCommands = new java.util.ArrayList<>();
        TestCompileService service = new TestCompileService(
                new BuildService(),
                new SourceDiscoverer(),
                new ResourceCopier(),
                new BuildFingerprintService(),
                new com.zolt.doctor.JdkDetector(),
                new JavacRunner(),
                new GroovyCompilerRunner(":", command -> {
                    groovyCommands.add(command);
                    return new GroovyCompilerRunner.ProcessResult(0, "groovy compiled\n");
                }));

        TestCompileResult result = service.compileTests(
                projectDir,
                config().withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        List.of("src/test/groovy"))),
                projectDir.resolve("cache"));

        assertEquals(2, result.sourceCount());
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/TestHelper.class")));
        assertEquals(1, groovyCommands.size());
        List<String> command = groovyCommands.getFirst();
        assertTrue(command.contains(groovySource.normalize().toString()));
        assertTrue(command.contains("-classpath"));
        String classpath = command.get(command.indexOf("-classpath") + 1);
        assertTrue(classpath.contains(projectDir.resolve("target/test-classes").toString()));
        assertTrue(classpath.contains(projectDir.resolve("target/classes").toString()));
        assertTrue(result.compilerOutput().contains("groovy compiled"));
    }

    @Test
    void compilesGroovyTestSourcesWithProjectProvidedCompilerJar() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path groovyJar = cacheRoot.resolve("org/apache/groovy/groovy/4.0.24/groovy-4.0.24.jar");
        createFakeGroovyCompilerJar(groovyJar);
        writeLockfile("""
                version = 1

                [[package]]
                id = "org.apache.groovy:groovy"
                version = "4.0.24"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/apache/groovy/groovy/4.0.24/groovy-4.0.24.jar"
                dependencies = []
                """);
        source("src/test/groovy/com/example/MainSpec.groovy", """
                package com.example

                final class MainSpec {
                }
                """);
        ProjectConfig config = config().withBuildSettings(new BuildSettings(
                "src/main/java",
                "src/test/java",
                "target/classes",
                "target/test-classes",
                List.of("src/test/java"),
                List.of("src/test/groovy")));

        TestCompileResult first = testCompileService.compileTests(projectDir, config, cacheRoot);
        TestCompileResult second = testCompileService.compileTests(projectDir, config, cacheRoot);

        assertEquals(1, first.sourceCount());
        assertEquals("full", first.testCompilationMode());
        assertEquals("groovy-test-sources", first.testIncrementalFallbackReason());
        assertTrue(first.compilerOutput().contains("fake groovy compiler"));
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/MainSpec.class")));
        IncrementalCompileState state = new IncrementalCompileStateCodec()
                .read(projectDir.resolve("target/test-classes/.zolt-incremental-test.state"))
                .orElseThrow();
        assertTrue(state.fallbackReasons().contains("groovy-test-sources"));
        assertTrue(state.fallbackReasons().contains("unreadable-class-output"));
        assertTrue(second.testCompilationSkipped());
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

    private void createFakeGroovyCompilerJar(Path jar) throws IOException {
        Path compilerSource = projectDir.resolve(
                "fake-groovy-compiler-src/org/codehaus/groovy/tools/FileSystemCompiler.java");
        Files.createDirectories(compilerSource.getParent());
        Files.writeString(compilerSource, """
                package org.codehaus.groovy.tools;

                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.ArrayList;
                import java.util.List;

                public final class FileSystemCompiler {
                    public static void main(String[] args) throws Exception {
                        Path output = null;
                        List<String> sources = new ArrayList<>();
                        for (int index = 0; index < args.length; index++) {
                            if ("-d".equals(args[index])) {
                                output = Path.of(args[++index]);
                            } else if ("-classpath".equals(args[index])) {
                                index++;
                            } else if (args[index].endsWith(".groovy")) {
                                sources.add(args[index]);
                            }
                        }
                        if (output == null) {
                            throw new IllegalArgumentException("-d is required");
                        }
                        for (String source : sources) {
                            String normalized = source.replace('\\\\', '/');
                            String marker = "/src/test/groovy/";
                            int markerIndex = normalized.indexOf(marker);
                            String relative = markerIndex >= 0
                                    ? normalized.substring(markerIndex + marker.length())
                                    : Path.of(source).getFileName().toString();
                            relative = relative.substring(0, relative.length() - ".groovy".length()) + ".class";
                            Path classFile = output.resolve(relative);
                            Files.createDirectories(classFile.getParent());
                            Files.write(classFile, new byte[] {0, 0});
                        }
                        System.out.println("fake groovy compiler");
                    }
                }
                """);
        Path classes = projectDir.resolve("fake-groovy-compiler-classes");
        new JavacRunner().compile(
                currentJavac(),
                List.of(compilerSource),
                new Classpath(List.of()),
                classes);

        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            JarEntry entry = new JarEntry("org/codehaus/groovy/tools/FileSystemCompiler.class");
            output.putNextEntry(entry);
            output.write(Files.readAllBytes(classes.resolve("org/codehaus/groovy/tools/FileSystemCompiler.class")));
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
