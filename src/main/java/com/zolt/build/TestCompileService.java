package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.Classpath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestCompileService {
    private final BuildService buildService;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final SourceDiscoverer sourceDiscoverer;
    private final JdkDetector jdkDetector;
    private final JavacRunner javacRunner;

    public TestCompileService() {
        this(
                new BuildService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new SourceDiscoverer(),
                new JdkDetector(),
                new JavacRunner());
    }

    TestCompileService(
            BuildService buildService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            SourceDiscoverer sourceDiscoverer,
            JdkDetector jdkDetector,
            JavacRunner javacRunner) {
        this.buildService = buildService;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.sourceDiscoverer = sourceDiscoverer;
        this.jdkDetector = jdkDetector;
        this.javacRunner = javacRunner;
    }

    public TestCompileResult compileTests(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        BuildResult buildResult = buildService.build(projectDirectory, config, cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        SourceDiscoveryResult sources = sourceDiscoverer.discover(projectDirectory, config.build());
        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new BuildException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }

        List<Path> testCompileEntries = new ArrayList<>();
        testCompileEntries.add(buildResult.outputDirectory());
        testCompileEntries.addAll(classpaths.test().entries());
        Path outputDirectory = projectDirectory.resolve(config.build().testOutput());
        JavacResult javacResult = javacRunner.compile(
                jdkStatus.javac().orElseThrow(),
                sources.testSources(),
                new Classpath(testCompileEntries),
                outputDirectory);
        return new TestCompileResult(
                buildResult,
                javacResult.sourceCount(),
                javacResult.outputDirectory(),
                javacResult.output());
    }
}
