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
import java.util.StringJoiner;

public final class TestRunService {
    private static final String CONSOLE_MAIN_CLASS = "org.junit.platform.console.ConsoleLauncher";

    private final TestCompileService testCompileService;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final JdkDetector jdkDetector;
    private final JavaRunner javaRunner;
    private final String pathSeparator;

    public TestRunService() {
        this(
                new TestCompileService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new JdkDetector(),
                new JavaRunner(),
                java.io.File.pathSeparator);
    }

    TestRunService(
            TestCompileService testCompileService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            JdkDetector jdkDetector,
            JavaRunner javaRunner,
            String pathSeparator) {
        this.testCompileService = testCompileService;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
        this.pathSeparator = pathSeparator;
    }

    public TestRunResult runTests(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        TestCompileResult compileResult = testCompileService.compileTests(projectDirectory, config, cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        List<Path> runnerClasspath = new ArrayList<>();
        runnerClasspath.add(compileResult.outputDirectory());
        runnerClasspath.add(compileResult.buildResult().outputDirectory());
        runnerClasspath.addAll(classpaths.test().entries());
        if (runnerClasspath.stream().noneMatch(TestRunService::isConsoleJar)) {
            throw new TestRunException(
                    "JUnit Platform Console is not present on the test classpath. "
                            + "Add org.junit.platform:junit-platform-console-standalone to [test.dependencies].");
        }

        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new BuildException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }
        JavaRunResult result = javaRunner.run(
                jdkStatus.java().orElseThrow(),
                new Classpath(runnerClasspath),
                CONSOLE_MAIN_CLASS,
                List.of(
                        "execute",
                        "--disable-banner",
                        "--class-path", joined(runnerClasspath),
                        "--scan-class-path",
                        "--details", "summary"));
        return new TestRunResult(compileResult, result.output());
    }

    private String joined(List<Path> classpath) {
        StringJoiner joiner = new StringJoiner(pathSeparator);
        for (Path entry : classpath) {
            joiner.add(entry.normalize().toString());
        }
        return joiner.toString();
    }

    private static boolean isConsoleJar(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith("junit-platform-console") && name.endsWith(".jar");
    }
}
