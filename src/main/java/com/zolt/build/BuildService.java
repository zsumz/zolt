package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.CompilerSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.Classpath;
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class BuildService {
    private final ResolveService resolveService;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final SourceDiscoverer sourceDiscoverer;
    private final ResourceCopier resourceCopier;
    private final BuildMetadataGenerator buildMetadataGenerator;
    private final BuildFingerprintService buildFingerprintService;
    private final JdkChecker jdkDetector;
    private final JavacRunner javacRunner;
    private final OpenApiGeneratedSourceService openApiGeneratedSourceService;
    private final IncrementalCompileStateRecorder incrementalCompileStateRecorder;
    private final IncrementalCompilePlanner incrementalCompilePlanner;

    public BuildService() {
        this(new JdkDetector());
    }

    public BuildService(JdkChecker jdkDetector) {
        this(
                new ResolveService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new SourceDiscoverer(),
                new ResourceCopier(),
                new BuildMetadataGenerator(),
                new BuildFingerprintService(),
                jdkDetector,
                new JavacRunner(),
                new OpenApiGeneratedSourceService(jdkDetector),
                new IncrementalCompileStateRecorder(),
                new IncrementalCompilePlanner());
    }

    BuildService(
            ResolveService resolveService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            SourceDiscoverer sourceDiscoverer,
            ResourceCopier resourceCopier,
            BuildMetadataGenerator buildMetadataGenerator,
            BuildFingerprintService buildFingerprintService,
            JdkChecker jdkDetector,
            JavacRunner javacRunner,
            OpenApiGeneratedSourceService openApiGeneratedSourceService) {
        this(
                resolveService,
                lockfileReader,
                classpathBuilder,
                sourceDiscoverer,
                resourceCopier,
                buildMetadataGenerator,
                buildFingerprintService,
                jdkDetector,
                javacRunner,
                openApiGeneratedSourceService,
                new IncrementalCompileStateRecorder(),
                new IncrementalCompilePlanner());
    }

    BuildService(
            ResolveService resolveService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            SourceDiscoverer sourceDiscoverer,
            ResourceCopier resourceCopier,
            BuildMetadataGenerator buildMetadataGenerator,
            BuildFingerprintService buildFingerprintService,
            JdkChecker jdkDetector,
            JavacRunner javacRunner,
            OpenApiGeneratedSourceService openApiGeneratedSourceService,
            IncrementalCompileStateRecorder incrementalCompileStateRecorder,
            IncrementalCompilePlanner incrementalCompilePlanner) {
        this.resolveService = resolveService;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.sourceDiscoverer = sourceDiscoverer;
        this.resourceCopier = resourceCopier;
        this.buildMetadataGenerator = buildMetadataGenerator;
        this.buildFingerprintService = buildFingerprintService;
        this.jdkDetector = jdkDetector;
        this.javacRunner = javacRunner;
        this.openApiGeneratedSourceService = openApiGeneratedSourceService;
        this.incrementalCompileStateRecorder = incrementalCompileStateRecorder;
        this.incrementalCompilePlanner = incrementalCompilePlanner;
    }

    public BuildResult build(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return build(projectDirectory, config, cacheRoot, false);
    }

    public BuildResult build(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean offline) {
        return buildWithClasspaths(projectDirectory, config, cacheRoot, offline).buildResult();
    }

    public BuildResultWithClasspaths buildWithClasspaths(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            boolean offline) {
        Path lockfilePath = projectDirectory.resolve("zolt.lock");
        Optional<ResolveResult> resolveResult = Optional.empty();
        if (!Files.isRegularFile(lockfilePath)) {
            resolveResult = Optional.of(resolveService.resolve(projectDirectory, config, cacheRoot, false, offline));
        }
        if (openApiToolingMissing(projectDirectory, config, offline)) {
            resolveResult = Optional.of(resolveService.resolve(projectDirectory, config, cacheRoot, false, offline));
        }

        ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
        List<ResolvedClasspathPackage> classpathPackages = lockfileReader.classpathPackages(lockfile, cacheRoot);
        ClasspathSet classpaths = classpathBuilder.build(classpathPackages);
        openApiGeneratedSourceService.generateMain(projectDirectory, config, classpathPackages);
        return new BuildResultWithClasspaths(
                build(projectDirectory, config, classpaths, resolveResult),
                classpaths,
                classpathPackages);
    }

    private boolean openApiToolingMissing(
            Path projectDirectory,
            ProjectConfig config,
            boolean offline) {
        if (!hasOpenApiGeneratedSources(config)) {
            return false;
        }
        Path lockfilePath = projectDirectory.resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath)) {
            return false;
        }
        ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
        boolean hasTool = lockfile.packages().stream()
                .anyMatch(lockPackage -> lockPackage.scope() == DependencyScope.TOOL_OPENAPI);
        if (hasTool) {
            return false;
        }
        if (offline) {
            throw new BuildException(
                    "OpenAPI generation requires locked tool artifacts in scope `tool-openapi`, but zolt.lock does not contain them. "
                            + "Run `zolt resolve` without --offline to seed the OpenAPI generator tooling, then retry.");
        }
        return true;
    }

    private static boolean hasOpenApiGeneratedSources(ProjectConfig config) {
        return config.build().generatedMainSources().stream()
                .anyMatch(step -> step.kind() == GeneratedSourceKind.OPENAPI)
                || config.build().generatedTestSources().stream()
                        .anyMatch(step -> step.kind() == GeneratedSourceKind.OPENAPI);
    }

    public BuildResult build(Path projectDirectory, ProjectConfig config, ClasspathSet classpaths) {
        return build(projectDirectory, config, classpaths, Optional.empty());
    }

    private BuildResult build(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            Optional<ResolveResult> resolveResult) {
        SourceDiscoveryResult sources = sourceDiscoverer.discover(projectDirectory, config.build());
        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new BuildException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }

        Path outputDirectory = projectDirectory.resolve(config.build().output());
        Path generatedSourcesDirectory = generatedSourcesDirectory(projectDirectory, config.compilerSettings().generatedSources());
        Path lockfilePath = projectDirectory.resolve("zolt.lock");
        long fingerprintCheckStarted = System.nanoTime();
        boolean compileSkipped = buildFingerprintService.isMainCompileCurrent(
                projectDirectory,
                config,
                lockfilePath,
                sources,
                classpaths,
                outputDirectory,
                generatedSourcesDirectory);
        long fingerprintCheckNanos = elapsedSince(fingerprintCheckStarted);
        CompileAttempt javacResult = compileMain(
                compileSkipped,
                projectDirectory,
                config,
                sources,
                classpaths,
                outputDirectory,
                generatedSourcesDirectory,
                jdkStatus);
        ResourceCopyResult resourceResult = resourceCopier.copyMainResources(projectDirectory, config);
        BuildMetadataResult metadataResult = buildMetadataGenerator.generate(projectDirectory, config, outputDirectory);
        long fingerprintWriteNanos = 0L;
        if (!compileSkipped) {
            long fingerprintWriteStarted = System.nanoTime();
            buildFingerprintService.writeMainCompileFingerprint(
                    projectDirectory,
                    config,
                    lockfilePath,
                    sources,
                    classpaths,
                    outputDirectory,
                    generatedSourcesDirectory);
            fingerprintWriteNanos = elapsedSince(fingerprintWriteStarted);
            incrementalCompileStateRecorder.recordMain(
                    projectDirectory,
                    config,
                    sources,
                    classpaths,
                    outputDirectory,
                    generatedSourcesDirectory);
        }
        return new BuildResult(
                resolveResult,
                javacResult.sourceCount(),
                resourceResult.resourceCount() + metadataResult.generatedCount(),
                javacResult.outputDirectory(),
                javacResult.output(),
                compileSkipped,
                compileSkipped ? "skipped" : javacResult.mode(),
                compileSkipped ? "" : javacResult.fallbackReason(),
                javacResult.diagnostics(),
                fingerprintCheckNanos,
                fingerprintWriteNanos);
    }

    private CompileAttempt compileMain(
            boolean compileSkipped,
            Path projectDirectory,
            ProjectConfig config,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory,
            JdkStatus jdkStatus) {
        if (compileSkipped) {
            return new CompileAttempt(
                    new JavacResult(sources.mainSources().size(), outputDirectory, ""),
                    "skipped",
                    "",
                    CompileDiagnostics.empty());
        }
        JavacOptions options = javacOptions(config);
        IncrementalCompilePlanner.Plan plan = incrementalCompilePlanner.planMain(
                projectDirectory,
                config,
                sources.mainSources(),
                classpaths.compile(),
                classpaths.processor(),
                outputDirectory,
                generatedSourcesDirectory);
        if (plan.incremental()) {
            JavacResult result;
            IncrementalCompilePlanner.IncrementalValidation validation;
            try {
                result = javacRunner.compile(
                        jdkStatus.javac().orElseThrow(),
                        plan.sourcesToCompile(),
                        incrementalClasspath(classpaths.compile(), outputDirectory),
                        outputDirectory,
                        classpaths.processor(),
                        generatedSourcesDirectory,
                        options);
                validation = incrementalCompilePlanner.validateAfterIncrementalCompile(plan);
                if (!validation.hasFallback() && !validation.additionalSources().isEmpty()) {
                    JavacResult dependentResult = javacRunner.compile(
                            jdkStatus.javac().orElseThrow(),
                            validation.additionalSources(),
                            incrementalClasspath(classpaths.compile(), outputDirectory),
                            outputDirectory,
                            classpaths.processor(),
                            generatedSourcesDirectory,
                            options);
                    result = new JavacResult(
                            result.sourceCount() + dependentResult.sourceCount(),
                            outputDirectory,
                            combinedOutput(result.output(), dependentResult.output()));
                }
            } catch (JavacException exception) {
                incrementalCompileStateRecorder.deleteMainState(outputDirectory);
                return new CompileAttempt(
                        javacRunner.compile(
                                jdkStatus.javac().orElseThrow(),
                                sources.mainSources(),
                                classpaths.compile(),
                                outputDirectory,
                                classpaths.processor(),
                                generatedSourcesDirectory,
                                options),
                        "full",
                        "incremental-javac-failed",
                        plan.fullDiagnostics(sources.mainSources().size()));
            }
            if (!validation.hasFallback()) {
                return new CompileAttempt(result, "incremental", "", plan.diagnostics(result.sourceCount(), validation));
            }
            incrementalCompileStateRecorder.deleteMainState(outputDirectory);
            deleteOwnedOutputs(plan);
            return new CompileAttempt(
                    javacRunner.compile(
                            jdkStatus.javac().orElseThrow(),
                            sources.mainSources(),
                            classpaths.compile(),
                            outputDirectory,
                            classpaths.processor(),
                            generatedSourcesDirectory,
                            options),
                    "full",
                    validation.fallbackReason(),
                    plan.fullDiagnostics(sources.mainSources().size()));
        }
        incrementalCompileStateRecorder.deleteMainState(outputDirectory);
        deleteOwnedOutputs(plan);
        return new CompileAttempt(
                javacRunner.compile(
                        jdkStatus.javac().orElseThrow(),
                        sources.mainSources(),
                        classpaths.compile(),
                        outputDirectory,
                        classpaths.processor(),
                        generatedSourcesDirectory,
                        options),
                "full",
                plan.fallbackReason(),
                plan.fullDiagnostics(sources.mainSources().size()));
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
                    "Invalid generated source output path `"
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
                compiler.args());
    }

    private static String effectiveRelease(ProjectConfig config) {
        String compilerRelease = config.compilerSettings().release();
        return compilerRelease.isBlank() ? config.project().java() : compilerRelease;
    }

    private static Classpath incrementalClasspath(Classpath classpath, Path outputDirectory) {
        List<Path> entries = new java.util.ArrayList<>();
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
                        "Could not delete stale compiled class "
                                + output
                                + ". Check that the build output directory is writable.",
                        exception);
            }
        }
    }

    private record CompileAttempt(
            JavacResult result,
            String mode,
            String fallbackReason,
            CompileDiagnostics diagnostics) {
        int sourceCount() {
            return result.sourceCount();
        }

        Path outputDirectory() {
            return result.outputDirectory();
        }

        String output() {
            return result.output();
        }
    }
}
