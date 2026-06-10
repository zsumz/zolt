package com.zolt.build;

import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.Classpath;
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
                new GroovyCompilerRunner());
    }

    TestCompileService(
            BuildService buildService,
            SourceDiscoverer sourceDiscoverer,
            ResourceCopier resourceCopier,
            BuildFingerprintService buildFingerprintService,
            JdkChecker jdkDetector,
            JavacRunner javacRunner,
            GroovyCompilerRunner groovyCompilerRunner) {
        this.buildService = buildService;
        this.sourceDiscoverer = sourceDiscoverer;
        this.resourceCopier = resourceCopier;
        this.buildFingerprintService = buildFingerprintService;
        this.jdkDetector = jdkDetector;
        this.javacRunner = javacRunner;
        this.groovyCompilerRunner = groovyCompilerRunner;
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
                compileTests(projectDirectory, config, buildResult.classpaths(), buildResult.buildResult()),
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
        boolean compileSkipped = buildFingerprintService.isTestCompileCurrent(
                projectDirectory,
                config,
                lockfilePath,
                sources,
                testCompileClasspath,
                classpaths.testProcessor(),
                outputDirectory,
                generatedSourcesDirectory);
        JavacResult javacResult = compileSkipped
                ? new JavacResult(sources.testSources().size(), outputDirectory, "")
                : javacRunner.compile(
                        jdkStatus.javac().orElseThrow(),
                        sources.testSources(),
                        testCompileClasspath,
                        outputDirectory,
                        classpaths.testProcessor(),
                        generatedSourcesDirectory);
        JavacResult groovyResult = compileSkipped
                ? new JavacResult(sources.groovyTestSources().size(), outputDirectory, "")
                : groovyCompilerRunner.compile(
                        jdkStatus.java().orElseThrow(),
                        sources.groovyTestSources(),
                        groovyCompileClasspath,
                        outputDirectory);
        ResourceCopyResult resourceResult = resourceCopier.copyTestResources(projectDirectory, config.build());
        buildFingerprintService.writeTestCompileFingerprint(
                projectDirectory,
                config,
                lockfilePath,
                sources,
                testCompileClasspath,
                classpaths.testProcessor(),
                outputDirectory,
                generatedSourcesDirectory);
        return new TestCompileResult(
                buildResult,
                javacResult.sourceCount() + groovyResult.sourceCount(),
                resourceResult.resourceCount(),
                javacResult.outputDirectory(),
                combinedOutput(javacResult.output(), groovyResult.output()),
                compileSkipped);
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
}
