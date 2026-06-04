package com.zolt.selfhost;

import com.zolt.build.BuildResult;
import com.zolt.build.BuildService;
import com.zolt.build.PackageResult;
import com.zolt.build.PackageService;
import com.zolt.build.TestRunResult;
import com.zolt.build.TestRunService;
import com.zolt.doctor.SelfHostingCheckResult;
import com.zolt.doctor.SelfHostingCheckService;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SelfCheckService {
    private final ZoltTomlParser tomlParser;
    private final SelfHostingCheckService selfHostingCheckService;
    private final LockedResolver lockedResolver;
    private final ProjectBuilder projectBuilder;
    private final TestRunner testRunner;
    private final ProjectPackager projectPackager;

    public SelfCheckService() {
        this(
                new ZoltTomlParser(),
                new SelfHostingCheckService(),
                (projectDirectory, config, cacheRoot, offline) -> new ResolveService()
                        .resolve(projectDirectory, config, cacheRoot, true, offline),
                (projectDirectory, config, cacheRoot, offline) -> new BuildService()
                        .build(projectDirectory, config, cacheRoot, offline),
                (projectDirectory, config, cacheRoot) -> new TestRunService()
                        .runTests(projectDirectory, config, cacheRoot),
                (projectDirectory, config, buildResult) -> new PackageService()
                        .packageJar(projectDirectory, config, buildResult));
    }

    SelfCheckService(
            ZoltTomlParser tomlParser,
            SelfHostingCheckService selfHostingCheckService,
            LockedResolver lockedResolver,
            ProjectBuilder projectBuilder,
            TestRunner testRunner,
            ProjectPackager projectPackager) {
        this.tomlParser = tomlParser;
        this.selfHostingCheckService = selfHostingCheckService;
        this.lockedResolver = lockedResolver;
        this.projectBuilder = projectBuilder;
        this.testRunner = testRunner;
        this.projectPackager = projectPackager;
    }

    public SelfCheckResult check(Path projectDirectory, Path cacheRoot, boolean offline) {
        Path root = projectDirectory.toAbsolutePath().normalize();
        List<SelfCheckResult.SelfCheckStep> steps = new ArrayList<>();
        ProjectConfig config;
        try {
            config = tomlParser.parse(root.resolve("zolt.toml"));
        } catch (RuntimeException exception) {
            steps.add(failed("config", exception.getMessage()));
            return new SelfCheckResult(steps);
        }

        SelfHostingCheckResult readiness = selfHostingCheckService.check(root, config);
        steps.add(new SelfCheckResult.SelfCheckStep(
                "doctor --self-hosting",
                readiness.ok(),
                readiness.ok()
                        ? "self-hosting readiness checks passed"
                        : firstFailure(readiness)));
        if (!readiness.ok()) {
            return new SelfCheckResult(steps);
        }

        ResolveResult resolveResult;
        try {
            resolveResult = lockedResolver.resolve(root, config, cacheRoot, offline);
            steps.add(new SelfCheckResult.SelfCheckStep(
                    "resolve --locked",
                    true,
                    "verified " + resolveResult.lockfilePath()));
        } catch (RuntimeException exception) {
            steps.add(failed("resolve --locked", exception.getMessage()));
            return new SelfCheckResult(steps);
        }

        BuildResult buildResult;
        try {
            buildResult = projectBuilder.build(root, config, cacheRoot, offline);
            steps.add(new SelfCheckResult.SelfCheckStep(
                    "build",
                    true,
                    "compiled " + buildResult.sourceCount() + " main source files"));
        } catch (RuntimeException exception) {
            steps.add(failed("build", exception.getMessage()));
            return new SelfCheckResult(steps);
        }

        try {
            TestRunResult testResult = testRunner.test(root, config, cacheRoot);
            steps.add(new SelfCheckResult.SelfCheckStep(
                    "test",
                    true,
                    "ran tests with " + testResult.compileResult().sourceCount() + " test source files"));
        } catch (RuntimeException exception) {
            steps.add(failed("test", exception.getMessage()));
            return new SelfCheckResult(steps);
        }

        try {
            PackageResult packageResult = projectPackager.packageJar(root, config, buildResult);
            steps.add(new SelfCheckResult.SelfCheckStep(
                    "package",
                    true,
                    "wrote " + packageResult.jarPath()));
        } catch (RuntimeException exception) {
            steps.add(failed("package", exception.getMessage()));
        }
        return new SelfCheckResult(steps);
    }

    private static SelfCheckResult.SelfCheckStep failed(String name, String message) {
        return new SelfCheckResult.SelfCheckStep(name, false, message == null ? "check failed" : message);
    }

    private static String firstFailure(SelfHostingCheckResult readiness) {
        return readiness.checks().stream()
                .filter(check -> !check.ok())
                .map(check -> check.name() + ": " + check.message())
                .findFirst()
                .orElse("self-hosting readiness failed");
    }

    @FunctionalInterface
    interface LockedResolver {
        ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean offline);
    }

    @FunctionalInterface
    interface ProjectBuilder {
        BuildResult build(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean offline);
    }

    @FunctionalInterface
    interface TestRunner {
        TestRunResult test(Path projectDirectory, ProjectConfig config, Path cacheRoot);
    }

    @FunctionalInterface
    interface ProjectPackager {
        PackageResult packageJar(Path projectDirectory, ProjectConfig config, BuildResult buildResult);
    }
}
