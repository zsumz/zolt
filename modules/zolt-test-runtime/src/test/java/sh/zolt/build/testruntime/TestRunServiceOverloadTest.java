package sh.zolt.build.testruntime;

import static sh.zolt.build.testruntime.TestRunServiceTestSupport.commandArgumentAfter;
import static sh.zolt.build.testruntime.TestRunServiceTestSupport.config;
import static sh.zolt.build.testruntime.TestRunServiceTestSupport.service;
import static sh.zolt.build.testruntime.TestRunServiceTestSupport.source;
import static sh.zolt.build.testruntime.TestRunServiceLockfileTestSupport.writeConsoleLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.build.BuildResult;
import sh.zolt.build.run.JavaRunner;
import sh.zolt.build.testruntime.TestRunServiceTestSupport.CachingJdkChecker;
import sh.zolt.build.testruntime.compile.TestCompileResult;
import sh.zolt.classpath.Classpath;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.framework.FrameworkTestRunner;
import sh.zolt.resolve.ResolveService;
import sh.zolt.test.runtime.TestJvmArguments;
import sh.zolt.test.TestSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestRunServiceOverloadTest {
    @TempDir
    private Path projectDir;

    @Test
    void runCompiledTestsShortOverloadUsesDefaultConsoleOptions() throws IOException {
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        TestRunResult result = service.runCompiledTests(
                projectDir,
                config(),
                classpathSetWithConsoleJar(),
                compileResult(1));

        assertEquals("Tests successful\n", result.output());
        assertEquals("junit-console", result.testRunner());
        assertTrue(result.testSelection().emptySelection());
        assertEquals(List.of(), result.testJvmArguments().values());
        assertEquals(Optional.empty(), result.reportsDirectory());
        assertEquals(3, result.testRuntimeClasspathEntries());
        assertEquals(1, result.testLauncherClasspathEntries());
        List<String> command = commands.getFirst();
        assertTrue(command.contains("--scan-class-path=" + testOutputDirectory().toAbsolutePath().normalize()));
        assertEquals("summary", commandArgumentAfter(command, "--details"));
        assertFalse(command.contains("--select-class"));
        assertFalse(command.contains("--reports-dir"));
    }

    @Test
    void runTestsWithPrebuiltBuildInputsCompilesTestsThenAppliesRuntimeOptions() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });
        BuildResult buildResult = prebuiltBuildResult();
        TestSelection selection = TestSelection.fromCli(List.of("com.example.MainTest"), List.of(), List.of(), List.of());

        TestRunResult result = service.runTests(
                projectDir,
                config(),
                classpathSetWithConsoleJar(),
                buildResult,
                selection,
                new TestJvmArguments(List.of("-Dprebuilt=true")),
                TestReportSettings.reportsDirectory(Path.of("target/prebuilt-reports")),
                List.of("failed"));

        Path reportsDirectory = projectDir.resolve("target/prebuilt-reports").toAbsolutePath().normalize();
        assertEquals(buildResult, result.compileResult().buildResult());
        assertEquals(1, result.compileResult().sourceCount());
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/MainTest.class")));
        assertEquals(selection, result.testSelection());
        assertEquals(List.of("-Dprebuilt=true"), result.testJvmArguments().values());
        assertEquals(Optional.of(reportsDirectory), result.reportsDirectory());
        List<String> command = commands.getFirst();
        assertTrue(command.contains("-Dprebuilt=true"));
        assertEquals("com.example.MainTest", commandArgumentAfter(command, "--select-class"));
        assertEquals(reportsDirectory.toString(), commandArgumentAfter(command, "--reports-dir"));
        assertEquals("tree", commandArgumentAfter(command, "--details"));
        assertEquals("ascii", commandArgumentAfter(command, "--details-theme"));
        assertFalse(command.stream().anyMatch(argument -> argument.startsWith("--scan-class-path=")));
    }

    @Test
    void publicConstructorOverloadsAreAvailableForDefaultWiring() {
        CachingJdkChecker jdkChecker = new CachingJdkChecker();
        FrameworkTestRunner frameworkTestRunner = FrameworkTestRunner.none();
        ResolveService resolveService = new ResolveService();

        assertNotNull(new TestRunService());
        assertNotNull(new TestRunService(jdkChecker));
        assertNotNull(new TestRunService(frameworkTestRunner));
        assertNotNull(new TestRunService(jdkChecker, frameworkTestRunner));
        assertNotNull(new TestRunService(frameworkTestRunner, resolveService));
        assertNotNull(new TestRunService(jdkChecker, frameworkTestRunner, resolveService));
    }

    private TestCompileResult compileResult(int sourceCount) throws IOException {
        return new TestCompileResult(
                prebuiltBuildResult(),
                sourceCount,
                0,
                testOutputDirectory(),
                "test classes were already compiled\n");
    }

    private BuildResult prebuiltBuildResult() throws IOException {
        Files.createDirectories(mainOutputDirectory());
        Files.createDirectories(testOutputDirectory());
        return new BuildResult(Optional.empty(), 0, 0, mainOutputDirectory(), "main classes were already built\n");
    }

    private ClasspathSet classpathSetWithConsoleJar() {
        Classpath empty = new Classpath(List.of());
        return new ClasspathSet(
                empty,
                empty,
                new Classpath(List.of(projectDir.resolve(
                        "cache/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"))),
                empty,
                empty,
                empty);
    }

    private Path mainOutputDirectory() {
        return projectDir.resolve("target/classes");
    }

    private Path testOutputDirectory() {
        return projectDir.resolve("target/test-classes");
    }
}
