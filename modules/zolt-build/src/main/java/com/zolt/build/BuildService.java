package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.Classpath;
import com.zolt.classpath.ClasspathSet;
import com.zolt.classpath.LockfileClasspathPackageConverter;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.build.generatedsource.OpenApiGeneratedSourceService;
import com.zolt.build.incremental.IncrementalCompileStateRecorder;
import com.zolt.build.springboot.SpringBootAotGenerationService;
import com.zolt.dependency.DependencyScope;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.generated.GeneratedSourceException;
import com.zolt.generated.ProtobufGeneratedSourceService;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
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
    private final OpenApiGeneratedSourceService openApiGeneratedSourceService;
    private final ProtobufGeneratedSourceService protobufGeneratedSourceService;
    private final SpringBootAotGenerationService springBootAotGenerationService;
    private final IncrementalCompileStateRecorder incrementalCompileStateRecorder;
    private final MainCompileSourceExecutor sourceExecutor;

    public BuildService() {
        this(new JdkDetector());
    }

    public BuildService(ResolveService resolveService) {
        this(new JdkDetector(), resolveService);
    }

    public BuildService(JdkChecker jdkDetector) {
        this(jdkDetector, new ResolveService());
    }

    public BuildService(JdkChecker jdkDetector, ResolveService resolveService) {
        this(BuildServiceDependencies.create(jdkDetector, resolveService));
    }

    BuildService(BuildServiceDependencies dependencies) {
        this.resolveService = dependencies.resolveService();
        this.lockfileReader = dependencies.lockfileReader();
        this.classpathBuilder = dependencies.classpathBuilder();
        this.sourceDiscoverer = dependencies.sourceDiscoverer();
        this.resourceCopier = dependencies.resourceCopier();
        this.buildMetadataGenerator = dependencies.buildMetadataGenerator();
        this.buildFingerprintService = dependencies.buildFingerprintService();
        this.jdkDetector = dependencies.jdkDetector();
        this.openApiGeneratedSourceService = dependencies.openApiGeneratedSourceService();
        this.protobufGeneratedSourceService = dependencies.protobufGeneratedSourceService();
        this.springBootAotGenerationService = dependencies.springBootAotGenerationService();
        this.incrementalCompileStateRecorder = dependencies.incrementalCompileStateRecorder();
        this.sourceExecutor = dependencies.sourceExecutor();
    }

    public BuildResult build(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return build(new BuildRequest(projectDirectory, config, cacheRoot, false));
    }

    public BuildResult build(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean offline) {
        return build(new BuildRequest(projectDirectory, config, cacheRoot, offline));
    }

    private BuildResult build(BuildRequest request) {
        return buildWithClasspaths(request).buildResult();
    }

    public BuildResultWithClasspaths buildWithClasspaths(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            boolean offline) {
        return buildWithClasspaths(new BuildRequest(projectDirectory, config, cacheRoot, offline));
    }

    private BuildResultWithClasspaths buildWithClasspaths(BuildRequest request) {
        Path lockfilePath = request.projectDirectory().resolve("zolt.lock");
        Optional<ResolveResult> resolveResult = Optional.empty();
        if (!Files.isRegularFile(lockfilePath)) {
            resolveResult = Optional.of(resolveService.resolve(
                    request.projectDirectory(),
                    request.config(),
                    request.cacheRoot(),
                    false,
                    request.offline()));
        }
        if (openApiToolingMissing(request)) {
            resolveResult = Optional.of(resolveService.resolve(
                    request.projectDirectory(),
                    request.config(),
                    request.cacheRoot(),
                    false,
                    request.offline()));
        }

        ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
        List<ResolvedClasspathPackage> classpathPackages = LockfileClasspathPackageConverter.classpathPackages(
                lockfile,
                request.cacheRoot());
        ClasspathSet classpaths = classpathBuilder.build(classpathPackages);
        openApiGeneratedSourceService.generateMain(request.projectDirectory(), request.config(), classpathPackages);
        try {
            protobufGeneratedSourceService.generateMain(request.projectDirectory(), request.config());
        } catch (GeneratedSourceException exception) {
            throw new BuildException(exception.getMessage(), exception);
        }
        return new BuildResultWithClasspaths(
                build(request.projectDirectory(), request.config(), classpaths, resolveResult, classpathPackages),
                classpaths,
                classpathPackages);
    }

    private boolean openApiToolingMissing(BuildRequest request) {
        if (!hasOpenApiGeneratedSources(request.config())) {
            return false;
        }
        Path lockfilePath = request.projectDirectory().resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath)) {
            return false;
        }
        ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
        boolean hasTool = lockfile.packages().stream()
                .anyMatch(lockPackage -> lockPackage.scope() == DependencyScope.TOOL_OPENAPI);
        if (hasTool) {
            return false;
        }
        if (request.offline()) {
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
        return build(projectDirectory, config, classpaths, Optional.empty(), List.of());
    }

    private BuildResult build(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            Optional<ResolveResult> resolveResult,
            List<ResolvedClasspathPackage> classpathPackages) {
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
        MainCompileSourceExecutor.Attempt javacResult = sourceExecutor.compile(
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
        springBootAotGenerationService.generate(
                projectDirectory,
                config,
                jdkStatus,
                classpaths,
                springBootAotClasspath(config, classpathPackages));
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

    private static Classpath springBootAotClasspath(
            ProjectConfig config,
            List<ResolvedClasspathPackage> classpathPackages) {
        if (!config.frameworkSettings().springBoot().nativeEnabled()) {
            return new Classpath(List.of());
        }
        return new Classpath(classpathPackages.stream()
                .filter(dependency -> dependency.scope() == DependencyScope.TOOL_SPRING_AOT)
                .map(dependency -> dependency.resolvedPackage().jarPath())
                .toList());
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

}

record BuildRequest(
        Path projectDirectory,
        ProjectConfig config,
        Path cacheRoot,
        boolean offline) {
    BuildRequest {
        Objects.requireNonNull(projectDirectory, "projectDirectory");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(cacheRoot, "cacheRoot");
    }
}
