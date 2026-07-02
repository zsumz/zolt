package sh.zolt.build.run;

import sh.zolt.build.BuildResultWithClasspaths;
import sh.zolt.build.BuildService;
import sh.zolt.build.packageplan.PackagePlanService;
import sh.zolt.build.packaging.PackageResult;
import sh.zolt.build.packaging.PackageService;
import sh.zolt.build.RunPackageException;
import sh.zolt.classpath.Classpath;
import sh.zolt.build.classpath.ClasspathBuilder;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.doctor.JdkChecker;
import sh.zolt.doctor.JdkDetector;
import sh.zolt.doctor.JdkStatus;
import sh.zolt.framework.FrameworkPackageAugmenter;
import sh.zolt.project.PackageMode;
import sh.zolt.project.ProjectConfig;
import sh.zolt.resolve.ResolveService;
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
        this(jdkDetector, new ResolveService(), frameworkPackageAugmenter);
    }

    public RunPackageService(ResolveService resolveService, FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this(resolveService, frameworkPackageAugmenter, new PackagePlanService());
    }

    public RunPackageService(
            ResolveService resolveService,
            FrameworkPackageAugmenter frameworkPackageAugmenter,
            PackagePlanService packagePlanService) {
        this(new JdkDetector(), resolveService, frameworkPackageAugmenter, packagePlanService);
    }

    public RunPackageService(
            JdkChecker jdkDetector,
            ResolveService resolveService,
            FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this(jdkDetector, resolveService, frameworkPackageAugmenter, new PackagePlanService());
    }

    public RunPackageService(
            JdkChecker jdkDetector,
            ResolveService resolveService,
            FrameworkPackageAugmenter frameworkPackageAugmenter,
            PackagePlanService packagePlanService) {
        this(
                new PackageService(resolveService, frameworkPackageAugmenter, packagePlanService),
                new BuildService(jdkDetector, resolveService),
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
                || packageResult.mode() == PackageMode.SPRING_BOOT_WAR
                || packageResult.mode() == PackageMode.UBER) {
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
