package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.Classpath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RunPackageService {
    private final PackageService packageService;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final JdkDetector jdkDetector;
    private final JavaRunner javaRunner;

    public RunPackageService() {
        this(
                new PackageService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new JdkDetector(),
                new JavaRunner());
    }

    RunPackageService(
            PackageService packageService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            JdkDetector jdkDetector,
            JavaRunner javaRunner) {
        this.packageService = packageService;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
    }

    public RunPackageResult runPackage(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            List<String> arguments) {
        String mainClass = config.project().main().orElseThrow(() -> new RunPackageException(
                "No main class is configured. Add [project].main to zolt.toml to run a packaged application."));
        PackageResult packageResult = packageService.packageJar(projectDirectory, config, cacheRoot);
        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new RunPackageException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }

        if (packageResult.mode() == PackageMode.SPRING_BOOT) {
            JavaRunResult javaRunResult = javaRunner.runJar(
                    jdkStatus.java().orElseThrow(),
                    packageResult.jarPath(),
                    mainClass,
                    arguments);
            return new RunPackageResult(packageResult, javaRunResult);
        }

        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot).stream()
                .filter(dependency -> dependency.scope().packagedByDefault())
                .toList());
        List<Path> runtimeEntries = new ArrayList<>();
        runtimeEntries.add(packageResult.jarPath());
        runtimeEntries.addAll(classpaths.runtime().entries());

        JavaRunResult javaRunResult = javaRunner.run(
                jdkStatus.java().orElseThrow(),
                new Classpath(runtimeEntries),
                mainClass,
                arguments);
        return new RunPackageResult(packageResult, javaRunResult);
    }
}
