package sh.zolt.build.testruntime;

import static sh.zolt.build.testruntime.TestRunServiceLockfileTestSupport.writeConsoleLockfile;
import static sh.zolt.build.testruntime.TestRunServiceTestSupport.config;
import static sh.zolt.build.testruntime.TestRunServiceTestSupport.fixedJdkStatus;
import static sh.zolt.build.testruntime.TestRunServiceTestSupport.service;
import static sh.zolt.build.testruntime.TestRunServiceTestSupport.source;
import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.build.junit.PlainJunitWorkerRunResult;
import sh.zolt.doctor.JdkChecker;
import sh.zolt.junit.JunitWorkerClient;
import sh.zolt.test.TestSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the compile/run toolchain split: tests compile with the build toolchain but the forked
 * worker launches with the SEPARATE run (test-runtime) toolchain's java executable.
 */
final class TestRunServiceTargetRuntimeTest {
    @TempDir
    private Path projectDir;

    @Test
    void forksWorkerWithRunToolchainJavaWhileCompilingWithBuildToolchain() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java",
                "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java",
                "package com.example; public final class MainTest {}\n");

        Path testRuntimeJava = projectDir.resolve("test-runtime-jdk/bin/java").toAbsolutePath().normalize();
        String projectJava = config().project().java();
        JdkChecker compileChecker = new TestRunServiceTestSupport.CachingJdkChecker();
        JdkChecker runChecker = requiredVersion -> fixedJdkStatus(testRuntimeJava, requiredVersion);

        List<Path> workerJavaExecutables = new ArrayList<>();
        TestRunService runService = service(
                compileChecker,
                runChecker,
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory,
                        testSelection, jvmArguments, environment, reportsDirectory, testEvents, profileDirectory) -> {
                    workerJavaExecutables.add(javaExecutable);
                    return new PlainJunitWorkerRunResult(
                            new JunitWorkerClient.WorkerRunResult("Tests found: 1\nTests succeeded: 1\n", 0),
                            10L,
                            20L);
                });

        runService.runTests(projectDir, config(), projectDir.resolve("cache"), TestSelection.empty());

        assertEquals(List.of(testRuntimeJava), workerJavaExecutables);
        assertEquals(projectJava, config().project().java());
    }
}
