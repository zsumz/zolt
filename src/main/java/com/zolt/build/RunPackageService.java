package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.Classpath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RunPackageService {
    private final PackageService packageService;
    private final BuildService buildService;
    private final ClasspathBuilder classpathBuilder;
    private final JdkChecker jdkDetector;
    private final JavaRunner javaRunner;

    public RunPackageService() {
        this(new JdkDetector());
    }

    public RunPackageService(JdkChecker jdkDetector) {
        this(jdkDetector, FrameworkPackageAugmenter.none());
    }

    public RunPackageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this(new JdkDetector(), frameworkPackageAugmenter);
    }

    public RunPackageService(JdkChecker jdkDetector, FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this(
                new PackageService(frameworkPackageAugmenter),
                new BuildService(jdkDetector),
                new ClasspathBuilder(),
                jdkDetector,
                new JavaRunner());
    }

    RunPackageService(
            PackageService packageService,
            BuildService buildService,
            ClasspathBuilder classpathBuilder,
            JdkChecker jdkDetector,
            JavaRunner javaRunner) {
        this.packageService = packageService;
        this.buildService = buildService;
        this.classpathBuilder = classpathBuilder;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
    }

    public RunPackageResult runPackage(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            List<String> arguments) {
        if (config.packageSettings().mode() == PackageMode.WAR) {
            throw new RunPackageException(
                    "Package mode `war` creates a servlet container deployment artifact and cannot be run directly. "
                            + "Deploy it to a servlet container, or use package mode `spring-boot-war` for java -jar.");
        }
        String mainClass = config.project().main().orElseThrow(() -> new RunPackageException(
                "No main class is configured. Add [project].main to zolt.toml to run a packaged application."));
        packageService.preparePackageToolingIfNeeded(projectDirectory, config, cacheRoot);
        BuildResultWithClasspaths buildResult = buildService.buildWithClasspaths(
                projectDirectory,
                config,
                cacheRoot,
                false);
        PackageResult packageResult = packageService.packageJar(projectDirectory, config, buildResult, cacheRoot);
        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new RunPackageException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }

        if (packageResult.mode() == PackageMode.SPRING_BOOT
                || packageResult.mode() == PackageMode.SPRING_BOOT_WAR) {
            JavaRunResult javaRunResult = javaRunner.runJar(
                    jdkStatus.java().orElseThrow(),
                    packageResult.jarPath(),
                    mainClass,
                    arguments);
            return new RunPackageResult(packageResult, javaRunResult);
        }

        ClasspathSet classpaths = classpathBuilder.build(buildResult.classpathPackages().stream()
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
