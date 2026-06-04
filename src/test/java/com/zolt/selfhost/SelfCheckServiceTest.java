package com.zolt.selfhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.BuildResult;
import com.zolt.build.JavaRunResult;
import com.zolt.build.NativeBuildResult;
import com.zolt.build.NativeImageResult;
import com.zolt.build.PackageResult;
import com.zolt.build.RunPackageResult;
import com.zolt.build.TestCompileResult;
import com.zolt.build.TestRunResult;
import com.zolt.doctor.SelfHostingCheckService;
import com.zolt.resolve.ResolveResult;
import com.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SelfCheckServiceTest {
    @TempDir
    private Path tempDir;

    @Test
    void runsJvmSelfHostingPathInOrder() throws IOException {
        writeSelfHostingProject(true);
        List<String> calls = new ArrayList<>();
        SelfCheckService service = new SelfCheckService(
                new ZoltTomlParser(),
                new SelfHostingCheckService(),
                (projectDirectory, config, cacheRoot, offline) -> {
                    calls.add("resolve:" + offline);
                    return new ResolveResult(3, 0, 0, projectDirectory.resolve("zolt.lock"));
                },
                (projectDirectory, config, cacheRoot, offline) -> {
                    calls.add("build:" + offline);
                    return buildResult(projectDirectory, 12);
                },
                (projectDirectory, config, cacheRoot) -> {
                    calls.add("test");
                    BuildResult buildResult = buildResult(projectDirectory, 12);
                    TestCompileResult compileResult = new TestCompileResult(
                            buildResult,
                            34,
                            0,
                            projectDirectory.resolve("target/test-classes"),
                            "");
                    return new TestRunResult(compileResult, "Tests passed\n");
                },
                (projectDirectory, config, buildResult, cacheRoot) -> {
                    calls.add("package:" + buildResult.sourceCount());
                    return new PackageResult(
                            buildResult,
                            projectDirectory.resolve("target/demo-0.1.0.jar"),
                            46,
                            true);
                },
                (projectDirectory, config, cacheRoot, packageResult) -> {
                    calls.add("run-package:" + packageResult.jarPath().getFileName());
                    return new RunPackageResult(
                            packageResult,
                            new JavaRunResult("com.example.Main", "demo 0.1.0\n"));
                },
                (projectDirectory, config, cacheRoot, nativeImageExecutable) -> {
                    throw new AssertionError("native should not run");
                },
                (binary, arguments) -> {
                    throw new AssertionError("native binary should not run");
                });

        SelfCheckResult result = service.check(tempDir, tempDir.resolve("cache"), true);

        assertTrue(result.ok());
        assertEquals(List.of(
                        "resolve:true",
                        "build:true",
                        "test",
                        "package:12",
                        "run-package:demo-0.1.0.jar"),
                calls);
        assertEquals(List.of(
                        "doctor --self-hosting",
                        "resolve --locked",
                        "build",
                        "test",
                        "package",
                        "run packaged jar"),
                result.steps().stream().map(SelfCheckResult.SelfCheckStep::name).toList());
        assertEquals("printed demo 0.1.0", result.steps().getLast().message());
    }

    @Test
    void stopsWhenReadinessCheckFails() throws IOException {
        writeSelfHostingProject(false);
        SelfCheckService service = new SelfCheckService(
                new ZoltTomlParser(),
                new SelfHostingCheckService(),
                (projectDirectory, config, cacheRoot, offline) -> {
                    throw new AssertionError("resolve should not run");
                },
                (projectDirectory, config, cacheRoot, offline) -> {
                    throw new AssertionError("build should not run");
                },
                (projectDirectory, config, cacheRoot) -> {
                    throw new AssertionError("test should not run");
                },
                (projectDirectory, config, buildResult, cacheRoot) -> {
                    throw new AssertionError("package should not run");
                },
                (projectDirectory, config, cacheRoot, packageResult) -> {
                    throw new AssertionError("run package should not run");
                },
                (projectDirectory, config, cacheRoot, nativeImageExecutable) -> {
                    throw new AssertionError("native should not run");
                },
                (binary, arguments) -> {
                    throw new AssertionError("native binary should not run");
                });

        SelfCheckResult result = service.check(tempDir, tempDir.resolve("cache"), false);

        assertFalse(result.ok());
        assertEquals(1, result.steps().size());
        assertEquals("doctor --self-hosting", result.steps().getFirst().name());
        assertTrue(result.steps().getFirst().message().contains("JUnit Platform Console"));
    }

    @Test
    void failsWhenPackagedApplicationDoesNotPrintVersion() throws IOException {
        writeSelfHostingProject(true);
        BuildResult buildResult = buildResult(tempDir, 12);
        PackageResult packageResult = new PackageResult(
                buildResult,
                tempDir.resolve("target/demo-0.1.0.jar"),
                46,
                true);
        SelfCheckService service = new SelfCheckService(
                new ZoltTomlParser(),
                new SelfHostingCheckService(),
                (projectDirectory, config, cacheRoot, offline) ->
                        new ResolveResult(3, 0, 0, projectDirectory.resolve("zolt.lock")),
                (projectDirectory, config, cacheRoot, offline) -> buildResult,
                (projectDirectory, config, cacheRoot) -> new TestRunResult(
                        new TestCompileResult(
                                buildResult,
                                34,
                                0,
                                projectDirectory.resolve("target/test-classes"),
                                ""),
                        "Tests passed\n"),
                (projectDirectory, config, suppliedBuildResult, cacheRoot) -> packageResult,
                (projectDirectory, config, cacheRoot, suppliedPackageResult) -> new RunPackageResult(
                        suppliedPackageResult,
                        new JavaRunResult("com.example.Main", "usage\n")),
                (projectDirectory, config, cacheRoot, nativeImageExecutable) -> {
                    throw new AssertionError("native should not run");
                },
                (binary, arguments) -> {
                    throw new AssertionError("native binary should not run");
                });

        SelfCheckResult result = service.check(tempDir, tempDir.resolve("cache"), false);

        assertFalse(result.ok());
        assertEquals("run packaged jar", result.steps().getLast().name());
        assertTrue(result.steps().getLast().message().contains("expected packaged application to print `demo 0.1.0`"));
    }

    @Test
    void runsNativeSelfHostingPathWhenRequested() throws IOException {
        writeSelfHostingProject(true);
        BuildResult buildResult = buildResult(tempDir, 12);
        PackageResult packageResult = new PackageResult(
                buildResult,
                tempDir.resolve("target/demo-0.1.0.jar"),
                46,
                true);
        NativeBuildResult nativeBuildResult = new NativeBuildResult(
                packageResult,
                new NativeImageResult(
                        tempDir.resolve("target/native/demo"),
                        tempDir.resolve("target/native/native-image.log"),
                        "native ok\n"));
        List<String> calls = new ArrayList<>();
        SelfCheckService service = new SelfCheckService(
                new ZoltTomlParser(),
                new SelfHostingCheckService(),
                (projectDirectory, config, cacheRoot, offline) ->
                        new ResolveResult(3, 0, 0, projectDirectory.resolve("zolt.lock")),
                (projectDirectory, config, cacheRoot, offline) -> buildResult,
                (projectDirectory, config, cacheRoot) -> new TestRunResult(
                        new TestCompileResult(
                                buildResult,
                                34,
                                0,
                                projectDirectory.resolve("target/test-classes"),
                                ""),
                        "Tests passed\n"),
                (projectDirectory, config, suppliedBuildResult, cacheRoot) -> packageResult,
                (projectDirectory, config, cacheRoot, suppliedPackageResult) -> new RunPackageResult(
                        suppliedPackageResult,
                        new JavaRunResult("com.example.Main", "demo 0.1.0\n")),
                (projectDirectory, config, cacheRoot, nativeImageExecutable) -> {
                    calls.add("native:" + nativeImageExecutable);
                    return nativeBuildResult;
                },
                (binary, arguments) -> {
                    calls.add("run-native:" + binary.getFileName() + ":" + arguments);
                    return new SelfCheckService.NativeBinaryRunResult(binary, "demo 0.1.0\n");
                });

        SelfCheckResult result = service.check(
                tempDir,
                tempDir.resolve("cache"),
                false,
                true,
                Path.of("custom-native-image"));

        assertTrue(result.ok());
        assertEquals(List.of("native:custom-native-image", "run-native:demo:[--version]"), calls);
        assertEquals(List.of(
                        "doctor --self-hosting",
                        "resolve --locked",
                        "build",
                        "test",
                        "package",
                        "run packaged jar",
                        "native",
                        "run native binary"),
                result.steps().stream().map(SelfCheckResult.SelfCheckStep::name).toList());
        assertEquals("printed demo 0.1.0", result.steps().getLast().message());
    }

    @Test
    void failsWhenNativeBinaryDoesNotPrintVersion() throws IOException {
        writeSelfHostingProject(true);
        BuildResult buildResult = buildResult(tempDir, 12);
        PackageResult packageResult = new PackageResult(
                buildResult,
                tempDir.resolve("target/demo-0.1.0.jar"),
                46,
                true);
        NativeBuildResult nativeBuildResult = new NativeBuildResult(
                packageResult,
                new NativeImageResult(
                        tempDir.resolve("target/native/demo"),
                        tempDir.resolve("target/native/native-image.log"),
                        "native ok\n"));
        SelfCheckService service = new SelfCheckService(
                new ZoltTomlParser(),
                new SelfHostingCheckService(),
                (projectDirectory, config, cacheRoot, offline) ->
                        new ResolveResult(3, 0, 0, projectDirectory.resolve("zolt.lock")),
                (projectDirectory, config, cacheRoot, offline) -> buildResult,
                (projectDirectory, config, cacheRoot) -> new TestRunResult(
                        new TestCompileResult(
                                buildResult,
                                34,
                                0,
                                projectDirectory.resolve("target/test-classes"),
                                ""),
                        "Tests passed\n"),
                (projectDirectory, config, suppliedBuildResult, cacheRoot) -> packageResult,
                (projectDirectory, config, cacheRoot, suppliedPackageResult) -> new RunPackageResult(
                        suppliedPackageResult,
                        new JavaRunResult("com.example.Main", "demo 0.1.0\n")),
                (projectDirectory, config, cacheRoot, nativeImageExecutable) -> nativeBuildResult,
                (binary, arguments) -> new SelfCheckService.NativeBinaryRunResult(binary, "usage\n"));

        SelfCheckResult result = service.check(
                tempDir,
                tempDir.resolve("cache"),
                false,
                true,
                Path.of("native-image"));

        assertFalse(result.ok());
        assertEquals("run native binary", result.steps().getLast().name());
        assertTrue(result.steps().getLast().message().contains("expected native binary to print `demo 0.1.0`"));
    }

    private BuildResult buildResult(Path projectDirectory, int sourceCount) {
        return new BuildResult(
                Optional.empty(),
                sourceCount,
                0,
                projectDirectory.resolve("target/classes"),
                "");
    }

    private void writeSelfHostingProject(boolean includeTestRunner) throws IOException {
        Files.writeString(tempDir.resolve("zolt.lock"), "version = 1\n");
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.createDirectories(tempDir.resolve("src/test/java"));
        Files.writeString(tempDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                main = "com.example.Main"

                [repositories]
                central = "https://repo.maven.apache.org/maven2"

                %s
                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"

                [native]
                imageName = "demo"
                output = "target/native"
                args = ["--no-fallback"]
                """.formatted(includeTestRunner
                ? """
                [test.dependencies]
                "org.junit.platform:junit-platform-console-standalone" = "1.11.4"

                """
                : ""));
    }
}
