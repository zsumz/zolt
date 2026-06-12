package com.zolt.build;

import com.zolt.classpath.ClasspathSet;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.project.CompilerSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.Classpath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestCompileService {
    private final BuildService buildService;
    private final SourceDiscoverer sourceDiscoverer;
    private final ResourceCopier resourceCopier;
    private final BuildFingerprintService buildFingerprintService;
    private final JdkChecker jdkDetector;
    private final JavacRunner javacRunner;
    private final GroovyCompilerRunner groovyCompilerRunner;
    private final OpenApiGeneratedSourceService openApiGeneratedSourceService;
    private final IncrementalCompileStateRecorder incrementalCompileStateRecorder;
    private final IncrementalCompilePlanner incrementalCompilePlanner;

    public TestCompileService() {
        this(new JdkDetector());
    }

    public TestCompileService(JdkChecker jdkDetector) {
        this(
                new BuildService(jdkDetector),
                new SourceDiscoverer(),
                new ResourceCopier(),
                new BuildFingerprintService(),
                jdkDetector,
                new JavacRunner(),
                new GroovyCompilerRunner(),
                new OpenApiGeneratedSourceService(jdkDetector),
                new IncrementalCompileStateRecorder(),
                new IncrementalCompilePlanner());
    }

    TestCompileService(
            BuildService buildService,
            SourceDiscoverer sourceDiscoverer,
            ResourceCopier resourceCopier,
            BuildFingerprintService buildFingerprintService,
            JdkChecker jdkDetector,
            JavacRunner javacRunner,
            GroovyCompilerRunner groovyCompilerRunner,
            OpenApiGeneratedSourceService openApiGeneratedSourceService) {
        this(
                buildService,
                sourceDiscoverer,
                resourceCopier,
                buildFingerprintService,
                jdkDetector,
                javacRunner,
                groovyCompilerRunner,
                openApiGeneratedSourceService,
                new IncrementalCompileStateRecorder(),
                new IncrementalCompilePlanner());
    }

    TestCompileService(
            BuildService buildService,
            SourceDiscoverer sourceDiscoverer,
            ResourceCopier resourceCopier,
            BuildFingerprintService buildFingerprintService,
            JdkChecker jdkDetector,
            JavacRunner javacRunner,
            GroovyCompilerRunner groovyCompilerRunner,
            OpenApiGeneratedSourceService openApiGeneratedSourceService,
            IncrementalCompileStateRecorder incrementalCompileStateRecorder,
            IncrementalCompilePlanner incrementalCompilePlanner) {
        this.buildService = buildService;
        this.sourceDiscoverer = sourceDiscoverer;
        this.resourceCopier = resourceCopier;
        this.buildFingerprintService = buildFingerprintService;
        this.jdkDetector = jdkDetector;
        this.javacRunner = javacRunner;
        this.groovyCompilerRunner = groovyCompilerRunner;
        this.openApiGeneratedSourceService = openApiGeneratedSourceService;
        this.incrementalCompileStateRecorder = incrementalCompileStateRecorder;
        this.incrementalCompilePlanner = incrementalCompilePlanner;
    }

    TestCompileService(
            BuildService buildService,
            SourceDiscoverer sourceDiscoverer,
            ResourceCopier resourceCopier,
            BuildFingerprintService buildFingerprintService,
            JdkChecker jdkDetector,
            JavacRunner javacRunner,
            GroovyCompilerRunner groovyCompilerRunner) {
        this(
                buildService,
                sourceDiscoverer,
                resourceCopier,
                buildFingerprintService,
                jdkDetector,
                javacRunner,
                groovyCompilerRunner,
                new OpenApiGeneratedSourceService(jdkDetector));
    }

    public TestCompileResult compileTests(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return compileTestsWithClasspaths(projectDirectory, config, cacheRoot).testCompileResult();
    }

    TestCompileResultWithClasspaths compileTestsWithClasspaths(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot) {
        BuildResultWithClasspaths buildResult = buildTestInputs(projectDirectory, config, cacheRoot);
        return new TestCompileResultWithClasspaths(
                compileTests(
                        projectDirectory,
                        config,
                        buildResult.classpaths(),
                        buildResult.buildResult(),
                        buildResult.classpathPackages()),
                buildResult.classpaths());
    }

    BuildResultWithClasspaths buildTestInputs(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return buildService.buildWithClasspaths(
                projectDirectory,
                config,
                cacheRoot,
                false);
    }

    public TestCompileResult compileTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult) {
        return compileTests(projectDirectory, config, classpaths, buildResult, List.of());
    }

    private TestCompileResult compileTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult,
            List<ResolvedClasspathPackage> classpathPackages) {
        openApiGeneratedSourceService.generateTest(projectDirectory, config, classpathPackages);
        SourceDiscoveryResult sources = sourceDiscoverer.discover(projectDirectory, config.build());
        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new BuildException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }

        List<Path> testCompileEntries = new ArrayList<>();
        testCompileEntries.add(buildResult.outputDirectory());
        testCompileEntries.addAll(classpaths.test().entries());
        Path outputDirectory = projectDirectory.resolve(config.build().testOutput());
        Classpath testCompileClasspath = new Classpath(testCompileEntries);
        List<Path> groovyCompileEntries = new ArrayList<>();
        groovyCompileEntries.add(outputDirectory);
        groovyCompileEntries.addAll(testCompileEntries);
        Classpath groovyCompileClasspath = new Classpath(groovyCompileEntries);
        Path generatedSourcesDirectory = generatedSourcesDirectory(projectDirectory, config.compilerSettings().generatedTestSources());
        Path lockfilePath = projectDirectory.resolve("zolt.lock");
        long fingerprintCheckStarted = System.nanoTime();
        boolean compileSkipped = buildFingerprintService.isTestCompileCurrent(
                projectDirectory,
                config,
                lockfilePath,
                sources,
                testCompileClasspath,
                classpaths.testProcessor(),
                outputDirectory,
                generatedSourcesDirectory);
        long fingerprintCheckNanos = elapsedSince(fingerprintCheckStarted);
        TestCompileAttempt compileAttempt = compileTestSources(
                compileSkipped,
                projectDirectory,
                config,
                sources,
                classpaths,
                testCompileClasspath,
                groovyCompileClasspath,
                outputDirectory,
                generatedSourcesDirectory,
                jdkStatus);
        ResourceCopyResult resourceResult = resourceCopier.copyTestResources(projectDirectory, config);
        long fingerprintWriteNanos = 0L;
        if (!compileSkipped) {
            long fingerprintWriteStarted = System.nanoTime();
            buildFingerprintService.writeTestCompileFingerprint(
                    projectDirectory,
                    config,
                    lockfilePath,
                    sources,
                    testCompileClasspath,
                    classpaths.testProcessor(),
                    outputDirectory,
                    generatedSourcesDirectory);
            fingerprintWriteNanos = elapsedSince(fingerprintWriteStarted);
            incrementalCompileStateRecorder.recordTest(
                    projectDirectory,
                    config,
                    sources,
                    testCompileClasspath,
                    classpaths.testProcessor(),
                    outputDirectory,
                    generatedSourcesDirectory);
        }
        return new TestCompileResult(
                buildResult,
                compileAttempt.sourceCount(),
                resourceResult.resourceCount(),
                compileAttempt.outputDirectory(),
                compileAttempt.output(),
                compileSkipped,
                compileSkipped ? "skipped" : compileAttempt.mode(),
                compileSkipped ? "" : compileAttempt.fallbackReason(),
                fingerprintCheckNanos,
                fingerprintWriteNanos);
    }

    private TestCompileAttempt compileTestSources(
            boolean compileSkipped,
            Path projectDirectory,
            ProjectConfig config,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Classpath testCompileClasspath,
            Classpath groovyCompileClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            JdkStatus jdkStatus) {
        if (compileSkipped) {
            return new TestCompileAttempt(
                    new JavacResult(sources.testSources().size(), outputDirectory, ""),
                    new JavacResult(sources.groovyTestSources().size(), outputDirectory, ""),
                    "skipped",
                    "");
        }
        JavacOptions options = javacOptions(config);
        IncrementalCompilePlanner.Plan plan = incrementalCompilePlanner.planTest(
                projectDirectory,
                config,
                sources,
                testCompileClasspath,
                classpaths.testProcessor(),
                outputDirectory,
                generatedSourcesDirectory);
        if (plan.incremental()) {
            JavacResult javacResult;
            IncrementalCompilePlanner.IncrementalValidation validation;
            try {
                javacResult = javacRunner.compile(
                        jdkStatus.javac().orElseThrow(),
                        plan.sourcesToCompile(),
                        incrementalClasspath(testCompileClasspath, outputDirectory),
                        outputDirectory,
                        classpaths.testProcessor(),
                        generatedSourcesDirectory,
                        options);
                validation = incrementalCompilePlanner.validateAfterIncrementalCompile(plan);
                if (!validation.hasFallback() && !validation.additionalSources().isEmpty()) {
                    JavacResult dependentResult = javacRunner.compile(
                            jdkStatus.javac().orElseThrow(),
                            validation.additionalSources(),
                            incrementalClasspath(testCompileClasspath, outputDirectory),
                            outputDirectory,
                            classpaths.testProcessor(),
                            generatedSourcesDirectory,
                            options);
                    javacResult = new JavacResult(
                            javacResult.sourceCount() + dependentResult.sourceCount(),
                            outputDirectory,
                            combinedOutput(javacResult.output(), dependentResult.output()));
                }
            } catch (JavacException exception) {
                incrementalCompileStateRecorder.deleteTestState(outputDirectory);
                return fullTestCompile(
                        jdkStatus,
                        sources,
                        testCompileClasspath,
                        groovyCompileClasspath,
                        outputDirectory,
                        generatedSourcesDirectory,
                        classpaths,
                        options,
                        "incremental-javac-failed");
            }
            if (!validation.hasFallback()) {
                return new TestCompileAttempt(
                        javacResult,
                        new JavacResult(0, outputDirectory, ""),
                        "incremental",
                        "");
            }
            incrementalCompileStateRecorder.deleteTestState(outputDirectory);
            deleteOwnedOutputs(plan);
            return fullTestCompile(
                    jdkStatus,
                    sources,
                    testCompileClasspath,
                    groovyCompileClasspath,
                    outputDirectory,
                    generatedSourcesDirectory,
                    classpaths,
                    options,
                    validation.fallbackReason());
        }
        incrementalCompileStateRecorder.deleteTestState(outputDirectory);
        deleteOwnedOutputs(plan);
        return fullTestCompile(
                jdkStatus,
                sources,
                testCompileClasspath,
                groovyCompileClasspath,
                outputDirectory,
                generatedSourcesDirectory,
                classpaths,
                options,
                plan.fallbackReason());
    }

    private TestCompileAttempt fullTestCompile(
            JdkStatus jdkStatus,
            SourceDiscoveryResult sources,
            Classpath testCompileClasspath,
            Classpath groovyCompileClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            ClasspathSet classpaths,
            JavacOptions options,
            String fallbackReason) {
        JavacResult javacResult = javacRunner.compile(
                jdkStatus.javac().orElseThrow(),
                sources.testSources(),
                testCompileClasspath,
                outputDirectory,
                classpaths.testProcessor(),
                generatedSourcesDirectory,
                options);
        JavacResult groovyResult = groovyCompilerRunner.compile(
                jdkStatus.java().orElseThrow(),
                sources.groovyTestSources(),
                groovyCompileClasspath,
                outputDirectory);
        return new TestCompileAttempt(javacResult, groovyResult, "full", fallbackReason);
    }

    private static long elapsedSince(long started) {
        return Math.max(0L, System.nanoTime() - started);
    }

    private static String combinedOutput(String first, String second) {
        if (first == null || first.isEmpty()) {
            return second == null ? "" : second;
        }
        if (second == null || second.isEmpty()) {
            return first;
        }
        if (first.endsWith("\n")) {
            return first + second;
        }
        return first + "\n" + second;
    }

    private static Path generatedSourcesDirectory(Path projectDirectory, String configuredPath) {
        Path configured = Path.of(configuredPath);
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        Path path = projectRoot.resolve(configured).normalize();
        if (configured.isAbsolute() || !path.startsWith(projectRoot) || path.equals(projectRoot)) {
            throw new BuildException(
                    "Invalid generated test source output path `"
                            + configuredPath
                            + "`. Use a project-relative path under the project directory.");
        }
        return path;
    }

    private static JavacOptions javacOptions(ProjectConfig config) {
        CompilerSettings compiler = config.compilerSettings();
        return new JavacOptions(
                effectiveRelease(config),
                compiler.encoding(),
                compiler.testArgs());
    }

    private static String effectiveRelease(ProjectConfig config) {
        String compilerRelease = config.compilerSettings().release();
        return compilerRelease.isBlank() ? config.project().java() : compilerRelease;
    }

    private static Classpath incrementalClasspath(Classpath classpath, Path outputDirectory) {
        List<Path> entries = new ArrayList<>();
        entries.add(outputDirectory);
        entries.addAll(classpath.entries());
        return new Classpath(entries);
    }

    private static void deleteOwnedOutputs(IncrementalCompilePlanner.Plan plan) {
        for (Path output : plan.outputsToDelete()) {
            try {
                Files.deleteIfExists(output);
            } catch (IOException exception) {
                throw new BuildException(
                        "Could not delete stale compiled test class "
                                + output
                                + ". Check that the test output directory is writable.",
                        exception);
            }
        }
    }

    private record TestCompileAttempt(
            JavacResult javacResult,
            JavacResult groovyResult,
            String mode,
            String fallbackReason) {
        int sourceCount() {
            return javacResult.sourceCount() + groovyResult.sourceCount();
        }

        Path outputDirectory() {
            return javacResult.outputDirectory();
        }

        String output() {
            return combinedOutput(javacResult.output(), groovyResult.output());
        }
    }
}
