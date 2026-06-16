package com.zolt.selfhost;

import static com.zolt.selfhost.SelfCheckServiceTestSupport.buildResult;
import static com.zolt.selfhost.SelfCheckServiceTestSupport.writeSelfHostingProject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SelfCheckServiceTest {
    @TempDir
    private Path tempDir;

    @Test
    void runsJvmSelfHostingPathInOrder() throws IOException {
        writeSelfHostingProject(tempDir, true);
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
                    com.zolt.build.BuildResult buildResult = buildResult(projectDirectory, 12);
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
                            new JavaRunResult("com.example.Main", "0.1.0\n"));
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
        assertEquals("printed 0.1.0", result.steps().getLast().message());
    }

    @Test
    void stopsWhenReadinessCheckFails() throws IOException {
        writeSelfHostingProject(tempDir, false);
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
        writeSelfHostingProject(tempDir, true);
        com.zolt.build.BuildResult buildResult = buildResult(tempDir, 12);
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
        assertTrue(result.steps().getLast().message().contains("expected packaged application to print only `0.1.0`"));
    }

    @Test
    void runsNativeSelfHostingPathWhenRequested() throws IOException {
        writeSelfHostingProject(tempDir, true);
        com.zolt.build.BuildResult buildResult = buildResult(tempDir, 12);
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
                        new JavaRunResult("com.example.Main", "0.1.0\n")),
                (projectDirectory, config, cacheRoot, nativeImageExecutable) -> {
                    calls.add("native:" + nativeImageExecutable);
                    return nativeBuildResult;
                },
                (binary, arguments) -> {
                    calls.add("run-native:" + binary.getFileName() + ":" + arguments);
                    return new SelfCheckService.NativeBinaryRunResult(binary, "0.1.0\n");
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
        assertEquals("printed 0.1.0", result.steps().getLast().message());
    }

    @Test
    void failsWhenNativeBinaryDoesNotPrintVersion() throws IOException {
        writeSelfHostingProject(tempDir, true);
        com.zolt.build.BuildResult buildResult = buildResult(tempDir, 12);
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
                        new JavaRunResult("com.example.Main", "0.1.0\n")),
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
        assertTrue(result.steps().getLast().message().contains("expected native binary to print only `0.1.0`"));
    }

}
