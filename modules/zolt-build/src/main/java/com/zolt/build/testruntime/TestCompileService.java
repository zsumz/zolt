package com.zolt.build.testruntime;

import com.zolt.classpath.Classpath;
import com.zolt.classpath.ClasspathSet;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.build.BuildException;
import com.zolt.build.fingerprint.BuildFingerprintService;
import com.zolt.build.BuildResult;
import com.zolt.build.BuildResultWithClasspaths;
import com.zolt.build.BuildService;
import com.zolt.build.GroovyCompilerRunner;
import com.zolt.build.JavacRunner;
import com.zolt.build.ResourceCopier;
import com.zolt.build.ResourceCopyResult;
import com.zolt.build.SourceDiscoverer;
import com.zolt.build.SourceDiscoveryResult;
import com.zolt.build.generatedsource.OpenApiGeneratedSourceService;
import com.zolt.build.incremental.IncrementalCompileStateRecorder;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.generated.GeneratedSourceException;
import com.zolt.generated.ProtobufGeneratedSourceService;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestCompileService {
    private final BuildService buildService;
    private final SourceDiscoverer sourceDiscoverer;
    private final ResourceCopier resourceCopier;
    private final BuildFingerprintService buildFingerprintService;
    private final JdkChecker jdkDetector;
    private final OpenApiGeneratedSourceService openApiGeneratedSourceService;
    private final ProtobufGeneratedSourceService protobufGeneratedSourceService;
    private final IncrementalCompileStateRecorder incrementalCompileStateRecorder;
    private final TestCompileSourceExecutor sourceExecutor;

    public TestCompileService() {
        this(new JdkDetector());
    }

    public TestCompileService(JdkChecker jdkDetector) {
        this(jdkDetector, new ResolveService());
    }

    public TestCompileService(JdkChecker jdkDetector, ResolveService resolveService) {
        this(TestCompileServiceDependencies.create(jdkDetector, resolveService));
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
        this(TestCompileServiceDependencies.create(
                buildService,
                sourceDiscoverer,
                resourceCopier,
                buildFingerprintService,
                jdkDetector,
                javacRunner,
                groovyCompilerRunner,
                openApiGeneratedSourceService));
    }

    TestCompileService(TestCompileServiceDependencies dependencies) {
        this.buildService = dependencies.buildService();
        this.sourceDiscoverer = dependencies.sourceDiscoverer();
        this.resourceCopier = dependencies.resourceCopier();
        this.buildFingerprintService = dependencies.buildFingerprintService();
        this.jdkDetector = dependencies.jdkDetector();
        this.openApiGeneratedSourceService = dependencies.openApiGeneratedSourceService();
        this.protobufGeneratedSourceService = dependencies.protobufGeneratedSourceService();
        this.incrementalCompileStateRecorder = dependencies.incrementalCompileStateRecorder();
        this.sourceExecutor = dependencies.sourceExecutor();
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
        try {
            protobufGeneratedSourceService.generateTest(projectDirectory, config);
        } catch (GeneratedSourceException exception) {
            throw new BuildException(exception.getMessage(), exception);
        }
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
        TestCompileSourceExecutor.Attempt compileAttempt = sourceExecutor.compile(
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
                compileAttempt.diagnostics(),
                fingerprintCheckNanos,
                fingerprintWriteNanos);
    }

    private static long elapsedSince(long started) {
        return Math.max(0L, System.nanoTime() - started);
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

}
