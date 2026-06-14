package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.classpath.Classpath;
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
    void repeatedTestCompilationSkipsJavacWhenInputsAreCurrent() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");

        TestCompileResult first = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        TestCompileResult second = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertFalse(first.testCompilationSkipped());
        assertFalse(first.buildResult().mainCompilationSkipped());
        assertEquals("full", first.testCompilationMode());
        assertEquals("missing-state", first.testIncrementalFallbackReason());
        assertTrue(second.buildResult().mainCompilationSkipped());
        assertTrue(second.testCompilationSkipped());
        assertEquals(1, second.sourceCount());
        assertTrue(second.buildResult().mainFingerprintCheckNanos() > 0);
        assertTrue(first.buildResult().mainFingerprintWriteNanos() > 0);
        assertEquals(0L, second.buildResult().mainFingerprintWriteNanos());
        assertTrue(second.testFingerprintCheckNanos() > 0);
        assertTrue(first.testFingerprintWriteNanos() > 0);
        assertEquals(0L, second.testFingerprintWriteNanos());
        assertTrue(Files.exists(projectDir.resolve("target/classes/.zolt-build-main.fingerprint.state")));
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/.zolt-build-test.fingerprint.state")));
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/MainTest.class")));
    }

    @Test
    void fullTestCompileWritesIncrementalOwnershipState() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        Path testSource = source("src/test/java/com/example/MainTest.java", """
                package com.example;

                public final class MainTest {
                    public String message() {
                        return new Helper().toString();
                    }

                    static final class Nested {
                    }
                }
                """);
        source("src/test/java/com/example/Helper.java", "package com.example; final class Helper {}\n");

        testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        IncrementalCompileState state = new IncrementalCompileStateCodec()
                .read(projectDir.resolve("target/test-classes/.zolt-incremental-test.state"))
                .orElseThrow();
        IncrementalCompileState.SourceRecord testRecord = state.sources().stream()
                .filter(source -> source.path().equals(testSource.toAbsolutePath().normalize()))
                .findFirst()
                .orElseThrow();
        assertEquals("test", state.scope());
        assertTrue(state.fallbackReasons().isEmpty());
        assertTrue(testRecord.classOutputs().contains(projectDir
                .resolve("target/test-classes/com/example/MainTest.class")
                .toAbsolutePath()
                .normalize()));
        assertTrue(testRecord.classOutputs().contains(projectDir
                .resolve("target/test-classes/com/example/MainTest$Nested.class")
                .toAbsolutePath()
                .normalize()));
        assertTrue(state.reverseDependencies().containsKey("com.example.Helper"));
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
    void bodyOnlyTestSourceChangeUsesIncrementalTestCompilation() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        Path testSource = source("src/test/java/com/example/MainTest.java", """
                package com.example;

                public final class MainTest {
                    public String message() {
                        return new Helper().toString() + " one";
                    }
                }
                """);
        source("src/test/java/com/example/Helper.java", "package com.example; final class Helper {}\n");
        testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(testSource, """
                package com.example;

                public final class MainTest {
                    public String message() {
                        return new Helper().toString() + " two";
                    }
                }
                """);

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertTrue(result.buildResult().mainCompilationSkipped());
        assertFalse(result.testCompilationSkipped());
        assertEquals("incremental", result.testCompilationMode());
        assertEquals("", result.testIncrementalFallbackReason());
        assertEquals(1, result.sourceCount());
    }

    @Test
    void abiChangingTestSourceWithoutDependentsStaysIncrementalTestCompilation() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        Path testSource = source("src/test/java/com/example/MainTest.java", """
                package com.example;

                public final class MainTest {
                    public String message() {
                        return "one";
                    }
                }
                """);
        testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(testSource, """
                package com.example;

                public final class MainTest {
                    public int message() {
                        return 2;
                    }
                }
                """);

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("incremental", result.testCompilationMode());
        assertEquals("", result.testIncrementalFallbackReason());
        assertEquals(1, result.sourceCount());
    }

    @Test
    void publicAbiChangeRecompilesTestSourcesThatReferenceChangedClass() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        Path apiSource = source("src/test/java/com/example/testapi/TestApi.java", """
                package com.example.testapi;

                public final class TestApi {
                    public static String message() {
                        return "one";
                    }
                }
                """);
        source("src/test/java/com/example/app/UseTestApi.java", """
                package com.example.app;

                import com.example.testapi.TestApi;

                public final class UseTestApi {
                    public String message() {
                        return TestApi.message();
                    }
                }
                """);
        testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(apiSource, """
                package com.example.testapi;

                public final class TestApi {
                    public static String message() {
                        return "two";
                    }

                    public static String extra() {
                        return "extra";
                    }
                }
                """);

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("incremental", result.testCompilationMode());
        assertEquals("", result.testIncrementalFallbackReason());
        assertEquals(2, result.sourceCount());
    }

    @Test
    void privateOnlyTestSourceChangeDoesNotRecompileDependents() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        Path apiSource = source("src/test/java/com/example/testapi/TestApi.java", """
                package com.example.testapi;

                public final class TestApi {
                    public static String message() {
                        return hidden();
                    }

                    private static String hidden() {
                        return "one";
                    }
                }
                """);
        source("src/test/java/com/example/app/UseTestApi.java", """
                package com.example.app;

                import com.example.testapi.TestApi;

                public final class UseTestApi {
                    public String message() {
                        return TestApi.message();
                    }
                }
                """);
        testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(apiSource, """
                package com.example.testapi;

                public final class TestApi {
                    public static String message() {
                        return hidden();
                    }

                    private static String hidden() {
                        return "two";
                    }
                }
                """);

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("incremental", result.testCompilationMode());
        assertEquals("", result.testIncrementalFallbackReason());
        assertEquals(1, result.sourceCount());
    }

    @Test
    void packagePrivateAbiChangeRecompilesSamePackageTestSources() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        Path apiSource = source("src/test/java/com/example/TestApi.java", """
                package com.example;

                public final class TestApi {
                    static String packageMessage() {
                        return "one";
                    }
                }
                """);
        source("src/test/java/com/example/NeighborTest.java", """
                package com.example;

                public final class NeighborTest {
                    public String message() {
                        return "neighbor";
                    }
                }
                """);
        testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(apiSource, """
                package com.example;

                public final class TestApi {
                    static int packageMessage() {
                        return 2;
                    }
                }
                """);

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("incremental", result.testCompilationMode());
        assertEquals("", result.testIncrementalFallbackReason());
        assertEquals(2, result.sourceCount());
    }

    @Test
    void deletedSourceFallsBackToFullTestCompilationAndDeletesOwnedClass() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", """
                package com.example;

                public final class MainTest {
                }
                """);
        Path deletedSource = source("src/test/java/com/example/DeletedTest.java", """
                package com.example;

                public final class DeletedTest {
                }
                """);
        testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        Files.delete(deletedSource);

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("full", result.testCompilationMode());
        assertEquals("source-deleted", result.testIncrementalFallbackReason());
        assertFalse(Files.exists(projectDir.resolve("target/test-classes/com/example/DeletedTest.class")));
    }

    @Test
    void failedIncrementalTestCompileInvalidatesStateForNextRun() throws IOException {
        writeLockfile("version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        Path testSource = source("src/test/java/com/example/MainTest.java", """
                package com.example;

                public final class MainTest {
                    public String message() {
                        return "one";
                    }
                }
                """);
        testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));
        Files.writeString(testSource, """
                package com.example;

                public final class MainTest {
                    broken
                }
                """);

        assertThrows(
                JavacException.class,
                () -> testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache")));

        assertFalse(Files.exists(projectDir.resolve("target/test-classes/.zolt-incremental-test.state")));
        Files.writeString(testSource, """
                package com.example;

                public final class MainTest {
                    public String message() {
                        return "fixed";
                    }
                }
                """);

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("full", result.testCompilationMode());
        assertEquals("missing-state", result.testIncrementalFallbackReason());
    }

    @Test
    void mainImplementationOnlyChangeDoesNotInvalidateTestCompileFingerprint() throws IOException {
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
        assertTrue(result.testCompilationSkipped());
    }

    @Test
    void mainAbiChangeInvalidatesTestCompileFingerprint() throws IOException {
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
                "package com.example; public final class Main { public static String message() { return \"two\"; } public static String extra() { return \"extra\"; } }\n");

        TestCompileResult result = testCompileService.compileTests(projectDir, config(), projectDir.resolve("cache"));

        assertFalse(result.buildResult().mainCompilationSkipped());
        assertFalse(result.testCompilationSkipped());
        assertEquals("full", result.testCompilationMode());
        assertEquals("compile-classpath-changed", result.testIncrementalFallbackReason());
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
