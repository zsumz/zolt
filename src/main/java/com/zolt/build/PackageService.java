package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.framework.FrameworkPackageResult;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPaths;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.resolve.ResolveService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class PackageService {
    private final BuildService buildService;
    private final ResolveService resolveService;
    private final ManifestGenerator manifestGenerator;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final FrameworkPackageAugmenter frameworkPackageAugmenter;
    private final PackagePlanService packagePlanService;
    private final PackageEvidenceManifestWriter evidenceManifestWriter;
    private final ThinJarLayoutAssembler thinJarLayoutAssembler;
    private final WarLayoutAssembler warLayoutAssembler;
    private final SpringBootJarLayoutAssembler springBootJarLayoutAssembler;
    private final SpringBootWarLayoutAssembler springBootWarLayoutAssembler;
    private final QuarkusFastJarLayoutAssembler quarkusFastJarLayoutAssembler;
    private final PackageSupplementalArtifactAssembler supplementalArtifactAssembler;
    private final PackageRuntimeJarSelector runtimeJarSelector;

    public PackageService() {
        this(FrameworkPackageAugmenter.none());
    }

    public PackageService(FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this(new ResolveService(), frameworkPackageAugmenter);
    }

    public PackageService(ResolveService resolveService, FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this(resolveService, frameworkPackageAugmenter, new PackagePlanService());
    }

    public PackageService(
            ResolveService resolveService,
            FrameworkPackageAugmenter frameworkPackageAugmenter,
            PackagePlanService packagePlanService) {
        this(
                new BuildService(resolveService),
                resolveService,
                new ManifestGenerator(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                frameworkPackageAugmenter,
                packagePlanService);
    }

    PackageService(
            BuildService buildService,
            ResolveService resolveService,
            ManifestGenerator manifestGenerator,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this(
                buildService,
                resolveService,
                manifestGenerator,
                lockfileReader,
                classpathBuilder,
                frameworkPackageAugmenter,
                new PackagePlanService());
    }

    PackageService(
            BuildService buildService,
            ResolveService resolveService,
            ManifestGenerator manifestGenerator,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            FrameworkPackageAugmenter frameworkPackageAugmenter,
            PackagePlanService packagePlanService) {
        this(
                buildService,
                resolveService,
                manifestGenerator,
                lockfileReader,
                classpathBuilder,
                frameworkPackageAugmenter,
                packagePlanService,
                new PackageEvidenceManifestWriter());
    }

    PackageService(
            BuildService buildService,
            ResolveService resolveService,
            ManifestGenerator manifestGenerator,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            FrameworkPackageAugmenter frameworkPackageAugmenter,
            PackagePlanService packagePlanService,
            PackageEvidenceManifestWriter evidenceManifestWriter) {
        this.buildService = buildService;
        this.resolveService = resolveService;
        this.manifestGenerator = manifestGenerator;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.frameworkPackageAugmenter = frameworkPackageAugmenter;
        this.packagePlanService = packagePlanService == null ? new PackagePlanService() : packagePlanService;
        this.evidenceManifestWriter = evidenceManifestWriter;
        this.thinJarLayoutAssembler = new ThinJarLayoutAssembler(
                manifestGenerator,
                lockfileReader,
                classpathBuilder);
        this.warLayoutAssembler = new WarLayoutAssembler(manifestGenerator);
        this.springBootJarLayoutAssembler = new SpringBootJarLayoutAssembler();
        this.springBootWarLayoutAssembler = new SpringBootWarLayoutAssembler();
        this.quarkusFastJarLayoutAssembler = new QuarkusFastJarLayoutAssembler();
        this.supplementalArtifactAssembler = new PackageSupplementalArtifactAssembler(classpathBuilder);
        this.runtimeJarSelector = new PackageRuntimeJarSelector();
    }

    public PackageResult packageJar(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        Path projectRoot = projectRoot(projectDirectory);
        PackageMode mode = config.packageSettings().mode();
        ensureSupportedPackageMode(mode);
        preparePackageToolingIfNeeded(projectRoot, config, cacheRoot);
        BuildResultWithClasspaths buildResult = buildService.buildWithClasspaths(
                projectRoot,
                config,
                cacheRoot,
                false);
        return packageJar(projectRoot, config, buildResult, cacheRoot);
    }

    public void preparePackageToolingIfNeeded(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        Path projectRoot = projectRoot(projectDirectory);
        if (!isSpringBootArchive(config.packageSettings().mode())) {
            return;
        }
        Path lockfilePath = projectRoot.resolve("zolt.lock");
        if (!Files.isRegularFile(lockfilePath)) {
            return;
        }
        ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
        if (containsSpringBootLoader(lockfile) || !canResolveSpringBootLoader(config)) {
            return;
        }
        resolveService.resolve(projectRoot, config, cacheRoot);
    }

    private static boolean containsSpringBootLoader(ZoltLockfile lockfile) {
        return lockfile.packages().stream()
                .anyMatch(lockPackage -> lockPackage.packageId().equals(SpringBootLoaderSupport.SPRING_BOOT_LOADER_PACKAGE)
                        && lockPackage.scope().entersMainRuntimeClasspath());
    }

    private static boolean canResolveSpringBootLoader(ProjectConfig config) {
        return !config.platforms().isEmpty()
                || config.dependencies().containsKey(SpringBootLoaderSupport.SPRING_BOOT_LOADER_PACKAGE.toString())
                || config.apiDependencies().containsKey(SpringBootLoaderSupport.SPRING_BOOT_LOADER_PACKAGE.toString());
    }

    private static boolean isSpringBootArchive(PackageMode mode) {
        return mode == PackageMode.SPRING_BOOT || mode == PackageMode.SPRING_BOOT_WAR;
    }

    public PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path cacheRoot) {
        PackageMode mode = config.packageSettings().mode();
        ensureSupportedPackageMode(mode);
        return packageJar(
                projectRoot(projectDirectory),
                config,
                buildResult,
                Optional.of(cacheRoot),
                Optional.empty(),
                Optional.empty());
    }

    public PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResultWithClasspaths buildResult,
            Path cacheRoot) {
        PackageMode mode = config.packageSettings().mode();
        ensureSupportedPackageMode(mode);
        return packageJar(
                projectRoot(projectDirectory),
                config,
                buildResult.buildResult(),
                Optional.of(cacheRoot),
                Optional.of(buildResult.classpathPackages()),
                Optional.empty());
    }

    public PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult) {
        PackageMode mode = config.packageSettings().mode();
        ensureSupportedPackageMode(mode);
        return packageJar(
                projectRoot(projectDirectory),
                config,
                buildResult,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            ClasspathSet classpaths) {
        PackageMode mode = config.packageSettings().mode();
        ensureSupportedPackageMode(mode);
        return packageJar(
                projectRoot(projectDirectory),
                config,
                buildResult,
                Optional.empty(),
                Optional.empty(),
                Optional.of(classpaths));
    }

    private static Path projectRoot(Path projectDirectory) {
        return projectDirectory.toAbsolutePath().normalize();
    }

    private PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Optional<Path> cacheRoot,
            Optional<List<ResolvedClasspathPackage>> classpathPackages,
            Optional<ClasspathSet> classpaths) {
        PackageMode mode = config.packageSettings().mode();
        PackageResult result = switch (mode) {
            case THIN -> thinJarLayoutAssembler.assemble(
                    projectDirectory,
                    config,
                    buildResult,
                    jarPath(projectDirectory, config),
                    cacheRoot,
                    classpathPackages);
            case SPRING_BOOT -> packageSpringBootJar(
                    projectDirectory,
                    config,
                    buildResult,
                    cacheRoot.orElseThrow(() -> new PackageException(
                            "Spring Boot package mode requires dependency jar access from zolt.lock. Use single-project `zolt package --mode spring-boot` for now; workspace Spring Boot packaging is not wired yet.")),
                    classpathPackages);
            case WAR -> packageWar(
                    projectDirectory,
                    config,
                    buildResult,
                    cacheRoot.orElseThrow(() -> new PackageException(
                            "WAR package mode requires dependency jar access from zolt.lock. Use single-project `zolt package --mode war` for now; workspace WAR packaging is not wired yet.")),
                    classpathPackages);
            case SPRING_BOOT_WAR -> packageSpringBootWar(
                    projectDirectory,
                    config,
                    buildResult,
                    cacheRoot.orElseThrow(() -> new PackageException(
                            "Spring Boot WAR package mode requires dependency jar access from zolt.lock. Use single-project `zolt package --mode spring-boot-war` for now; workspace Spring Boot WAR packaging is not wired yet.")),
                    classpathPackages);
            case QUARKUS -> packageFrameworkJar(
                    projectDirectory,
                    config,
                    buildResult,
                    mode,
                    cacheRoot.orElseThrow(() -> new PackageException(
                            "Framework package mode `"
                                    + mode.configValue()
                                    + "` requires dependency jar access from zolt.lock. Use single-project `zolt package --mode "
                                    + mode.configValue()
                                    + "` for now; workspace framework packaging is not wired yet.")));
            case UBER -> throw unsupportedPackageMode(mode);
        };
        List<PackageArtifact> artifacts = supplementalArtifactAssembler.assemble(
                projectDirectory,
                config,
                buildResult,
                classpathPackages,
                classpaths);
        PackagePlan plan = packagePlan(projectDirectory, config, result);
        Path evidenceManifest = evidenceManifestWriter.write(projectDirectory, config, plan, result, artifacts);
        return result.withArtifactsAndEvidence(artifacts, Optional.of(evidenceManifest));
    }

    private PackagePlan packagePlan(
            Path projectDirectory,
            ProjectConfig config,
            PackageResult result) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        if (Files.isRegularFile(projectRoot.resolve("zolt.lock"))) {
            return packagePlanService.plan(projectRoot, config);
        }
        return new PackagePlan(
                projectRoot,
                result.mode(),
                result.jarPath(),
                result.buildResult().outputDirectory(),
                result.applicationLayout(),
                result.runtimeClasspathPath(),
                List.of(),
                List.of());
    }

    private PackageResult packageSpringBootJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path cacheRoot,
            Optional<List<ResolvedClasspathPackage>> classpathPackages) {
        String startClass = config.project().main().orElseThrow(() -> new PackageException(
                "Spring Boot package mode requires [project].main in zolt.toml. Add the application main class and retry."));
        Path outputDirectory = requireOutputDirectory(buildResult);
        Path jarPath = jarPath(projectDirectory, config);
        List<PackageRuntimeJar> runtimeJars = classpathPackages
                .map(runtimeJarSelector::runtimeJars)
                .orElseGet(() -> runtimeJarSelector.runtimeJars(
                        lockfileReader.read(projectDirectory.resolve("zolt.lock")),
                        cacheRoot));
        return springBootJarLayoutAssembler.assemble(startClass, buildResult, outputDirectory, jarPath, runtimeJars);
    }

    private PackageResult packageWar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path cacheRoot,
            Optional<List<ResolvedClasspathPackage>> classpathPackages) {
        Path outputDirectory = requireOutputDirectory(buildResult);
        Path warPath = archivePath(projectDirectory, config, "war");
        List<ResolvedClasspathPackage> resolvedPackages = classpathPackages
                .orElseGet(() -> runtimeJarSelector.packagedClasspathPackages(
                        lockfileReader.read(projectDirectory.resolve("zolt.lock")),
                        cacheRoot));
        List<PackageRuntimeJar> runtimeJars = runtimeJarSelector.runtimeJarsWithoutProvidedDuplicates(resolvedPackages);
        return warLayoutAssembler.assemble(config, buildResult, outputDirectory, warPath, runtimeJars);
    }

    private PackageResult packageSpringBootWar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path cacheRoot,
            Optional<List<ResolvedClasspathPackage>> classpathPackages) {
        String startClass = config.project().main().orElseThrow(() -> new PackageException(
                "Spring Boot WAR package mode requires [project].main in zolt.toml. Add the application main class and retry."));
        Path outputDirectory = requireOutputDirectory(buildResult);
        Path warPath = archivePath(projectDirectory, config, "war");
        List<ResolvedClasspathPackage> resolvedPackages = classpathPackages
                .orElseGet(() -> runtimeJarSelector.allClasspathPackages(
                        lockfileReader.read(projectDirectory.resolve("zolt.lock")),
                        cacheRoot));
        List<PackageRuntimeJar> providedJars = runtimeJarSelector.providedJars(resolvedPackages);
        List<PackageRuntimeJar> runtimeJars = runtimeJarSelector.runtimeJarsWithoutProvidedDuplicates(resolvedPackages);
        return springBootWarLayoutAssembler.assemble(
                startClass,
                buildResult,
                outputDirectory,
                warPath,
                runtimeJars,
                providedJars);
    }

    private PackageResult packageFrameworkJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            PackageMode mode,
            Path cacheRoot) {
        Optional<FrameworkPackageResult> result = frameworkPackageAugmenter.augmentIfEnabled(
                projectDirectory,
                config,
                cacheRoot);
        FrameworkPackageResult packageResult = result.orElseThrow(() -> new PackageException(
                frameworkPackageAugmenter.missingPackageResultMessage(mode)));
        return quarkusFastJarLayoutAssembler.assemble(
                buildResult,
                mode,
                packageResult,
                frameworkPackageAugmenter.missingRunnerJarMessage(mode, packageResult.runnerJar()),
                frameworkPackageAugmenter.inspectPackageDirectoryMessage(mode, packageResult.packageDirectory()));
    }

    private static void ensureSupportedPackageMode(PackageMode mode) {
        if (mode != PackageMode.UBER) {
            return;
        }
        throw unsupportedPackageMode(mode);
    }

    private static PackageException unsupportedPackageMode(PackageMode mode) {
        return new PackageException(
                "Package mode `"
                        + mode.configValue()
                        + "` is not implemented yet. Supported package modes are: "
                        + supportedPackageModes()
                        + ". Intentionally unsupported package modes are: "
                        + unsupportedPackageModes()
                        + ". Use one of the supported modes until uber jar support lands"
                        + ".");
    }

    private static String supportedPackageModes() {
        return List.of(
                        PackageMode.THIN,
                        PackageMode.SPRING_BOOT,
                        PackageMode.WAR,
                        PackageMode.SPRING_BOOT_WAR,
                        PackageMode.QUARKUS)
                .stream()
                .map(PackageMode::configValue)
                .collect(Collectors.joining(", "));
    }

    private static String unsupportedPackageModes() {
        return List.of(PackageMode.UBER).stream()
                .map(PackageMode::configValue)
                .collect(Collectors.joining(", "));
    }

    private Path requireOutputDirectory(BuildResult buildResult) {
        Path outputDirectory = buildResult.outputDirectory();
        if (!Files.isDirectory(outputDirectory)) {
            throw new PackageException(
                    "Build output directory does not exist at "
                            + outputDirectory
                            + ". Run zolt build and check [build].output in zolt.toml.");
        }
        return outputDirectory;
    }

    private static Path jarPath(Path projectDirectory, ProjectConfig config) {
        return archivePath(projectDirectory, config, "jar");
    }

    private static Path archivePath(Path projectDirectory, ProjectConfig config, String extension) {
        return ProjectPaths.output(
                projectDirectory,
                "package archive",
                "target/" + artifactBaseName(config) + "." + extension);
    }

    private static String artifactBaseName(ProjectConfig config) {
        return ProjectPaths.filenameComponent("[project].name", config.project().name())
                + "-"
                + ProjectPaths.filenameComponent("[project].version", config.project().version());
    }

}
