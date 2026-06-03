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
    private final JdkDetector jdkDetector;
    private final JavacRunner javacRunner;

    public BuildService() {
        this(
                new ResolveService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new SourceDiscoverer(),
                new ResourceCopier(),
                new JdkDetector(),
                new JavacRunner());
    }

    BuildService(
            ResolveService resolveService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            SourceDiscoverer sourceDiscoverer,
            ResourceCopier resourceCopier,
            JdkDetector jdkDetector,
            JavacRunner javacRunner) {
        this.resolveService = resolveService;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.sourceDiscoverer = sourceDiscoverer;
        this.resourceCopier = resourceCopier;
        this.jdkDetector = jdkDetector;
        this.javacRunner = javacRunner;
    }

    public BuildResult build(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        Path lockfilePath = projectDirectory.resolve("zolt.lock");
        Optional<ResolveResult> resolveResult = Optional.empty();
        if (!Files.isRegularFile(lockfilePath)) {
            resolveResult = Optional.of(resolveService.resolve(projectDirectory, config, cacheRoot));
        }

        ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        SourceDiscoveryResult sources = sourceDiscoverer.discover(projectDirectory, config.build());
        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new BuildException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }

        Path outputDirectory = projectDirectory.resolve(config.build().output());
        JavacResult javacResult = javacRunner.compile(
                jdkStatus.javac().orElseThrow(),
                sources.mainSources(),
                classpaths.compile(),
                outputDirectory);
        ResourceCopyResult resourceResult = resourceCopier.copyMainResources(projectDirectory, config.build());
        return new BuildResult(
                resolveResult,
                javacResult.sourceCount(),
                resourceResult.copiedCount(),
                javacResult.outputDirectory(),
                javacResult.output());
    }
}
