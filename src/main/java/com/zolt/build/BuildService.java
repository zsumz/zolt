package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class BuildService {
    private final ResolveService resolveService;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final SourceDiscoverer sourceDiscoverer;
    private final ResourceCopier resourceCopier;
    private final BuildMetadataGenerator buildMetadataGenerator;
    private final BuildFingerprintService buildFingerprintService;
    private final JdkDetector jdkDetector;
    private final JavacRunner javacRunner;

    public BuildService() {
        this(
                new ResolveService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new SourceDiscoverer(),
                new ResourceCopier(),
                new BuildMetadataGenerator(),
                new BuildFingerprintService(),
                new JdkDetector(),
                new JavacRunner());
    }

    BuildService(
            ResolveService resolveService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            SourceDiscoverer sourceDiscoverer,
            ResourceCopier resourceCopier,
            BuildMetadataGenerator buildMetadataGenerator,
            BuildFingerprintService buildFingerprintService,
            JdkDetector jdkDetector,
            JavacRunner javacRunner) {
        this.resolveService = resolveService;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.sourceDiscoverer = sourceDiscoverer;
        this.resourceCopier = resourceCopier;
        this.buildMetadataGenerator = buildMetadataGenerator;
        this.buildFingerprintService = buildFingerprintService;
        this.jdkDetector = jdkDetector;
        this.javacRunner = javacRunner;
    }

    public BuildResult build(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return build(projectDirectory, config, cacheRoot, false);
    }

    public BuildResult build(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean offline) {
        return buildWithClasspaths(projectDirectory, config, cacheRoot, offline).buildResult();
    }

    BuildResultWithClasspaths buildWithClasspaths(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            boolean offline) {
        Path lockfilePath = projectDirectory.resolve("zolt.lock");
        Optional<ResolveResult> resolveResult = Optional.empty();
        if (!Files.isRegularFile(lockfilePath)) {
            resolveResult = Optional.of(resolveService.resolve(projectDirectory, config, cacheRoot, false, offline));
        }

        ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        return new BuildResultWithClasspaths(
                build(projectDirectory, config, classpaths, resolveResult),
                classpaths);
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
        boolean compileSkipped = buildFingerprintService.isMainCompileCurrent(
                projectDirectory,
                config,
                lockfilePath,
                sources,
                classpaths,
                outputDirectory,
                generatedSourcesDirectory);
        JavacResult javacResult = compileSkipped
                ? new JavacResult(sources.mainSources().size(), outputDirectory, "")
                : javacRunner.compile(
                        jdkStatus.javac().orElseThrow(),
                        sources.mainSources(),
                        classpaths.compile(),
                        outputDirectory,
                        classpaths.processor(),
                        generatedSourcesDirectory);
        ResourceCopyResult resourceResult = resourceCopier.copyMainResources(projectDirectory, config.build());
        BuildMetadataResult metadataResult = buildMetadataGenerator.generate(projectDirectory, config, outputDirectory);
        buildFingerprintService.writeMainCompileFingerprint(
                projectDirectory,
                config,
                lockfilePath,
                sources,
                classpaths,
                outputDirectory,
                generatedSourcesDirectory);
        return new BuildResult(
                resolveResult,
                javacResult.sourceCount(),
                resourceResult.resourceCount() + metadataResult.generatedCount(),
                javacResult.outputDirectory(),
                javacResult.output(),
                compileSkipped);
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
