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
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
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
                new OpenApiGeneratedSourceService(jdkDetector));
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
        JavacResult javacResult = compileSkipped
                ? new JavacResult(sources.mainSources().size(), outputDirectory, "")
                : javacRunner.compile(
                        jdkStatus.javac().orElseThrow(),
                        sources.mainSources(),
                        classpaths.compile(),
                        outputDirectory,
                        classpaths.processor(),
                        generatedSourcesDirectory,
                        javacOptions(config));
        ResourceCopyResult resourceResult = resourceCopier.copyMainResources(projectDirectory, config);
        BuildMetadataResult metadataResult = buildMetadataGenerator.generate(projectDirectory, config, outputDirectory);
        long fingerprintWriteStarted = System.nanoTime();
        buildFingerprintService.writeMainCompileFingerprint(
                projectDirectory,
                config,
                lockfilePath,
                sources,
                classpaths,
                outputDirectory,
                generatedSourcesDirectory);
        long fingerprintWriteNanos = elapsedSince(fingerprintWriteStarted);
        return new BuildResult(
                resolveResult,
                javacResult.sourceCount(),
                resourceResult.resourceCount() + metadataResult.generatedCount(),
                javacResult.outputDirectory(),
                javacResult.output(),
                compileSkipped,
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
}
