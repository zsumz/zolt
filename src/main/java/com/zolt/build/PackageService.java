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
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.resolve.LockfileClasspathPackageConverter;
import com.zolt.resolve.ResolvedClasspathPackage;
import com.zolt.resolve.ResolveService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class PackageService {
    private static final Set<String> LOCAL_BUILD_FINGERPRINTS = Set.of(
            ".zolt-build-main.fingerprint",
            ".zolt-build-main.fingerprint.state",
            ".zolt-build-test.fingerprint",
            ".zolt-build-test.fingerprint.state",
            ".zolt-incremental-main.state",
            ".zolt-incremental-test.state");

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
        List<PackageArtifact> artifacts = packageSupplementalArtifacts(
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
                .map(this::runtimeJars)
                .orElseGet(() -> runtimeJars(lockfileReader.read(projectDirectory.resolve("zolt.lock")), cacheRoot));
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
                .orElseGet(() -> packagedClasspathPackages(
                        lockfileReader.read(projectDirectory.resolve("zolt.lock")),
                        cacheRoot));
        List<PackageRuntimeJar> runtimeJars = runtimeJarsWithoutProvidedDuplicates(resolvedPackages);
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
                .orElseGet(() -> LockfileClasspathPackageConverter.classpathPackages(
                        lockfileReader.read(projectDirectory.resolve("zolt.lock")),
                        cacheRoot));
        List<PackageRuntimeJar> providedJars = providedJars(resolvedPackages);
        List<PackageRuntimeJar> runtimeJars = runtimeJarsWithoutProvidedDuplicates(resolvedPackages);
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

    private static Path classifierJarPath(Path projectDirectory, ProjectConfig config, String classifier) {
        return ProjectPaths.output(
                projectDirectory,
                "package artifact",
                "target/" + artifactBaseName(config) + "-" + classifier + ".jar");
    }

    private static String artifactBaseName(ProjectConfig config) {
        return ProjectPaths.filenameComponent("[project].name", config.project().name())
                + "-"
                + ProjectPaths.filenameComponent("[project].version", config.project().version());
    }

    private List<PackageArtifact> packageSupplementalArtifacts(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Optional<List<ResolvedClasspathPackage>> classpathPackages,
            Optional<ClasspathSet> classpaths) {
        List<PackageArtifact> artifacts = new ArrayList<>();
        if (config.packageSettings().sources()) {
            artifacts.add(packageSourcesJar(projectDirectory, config));
        }
        if (config.packageSettings().javadoc()) {
            artifacts.add(packageJavadocJar(projectDirectory, config, buildResult, classpathPackages, classpaths));
        }
        if (config.packageSettings().tests()) {
            artifacts.add(packageTestJar(projectDirectory, config));
        }
        return List.copyOf(artifacts);
    }

    private static PackageArtifact packageSourcesJar(Path projectDirectory, ProjectConfig config) {
        Path sourceRoot = ProjectPaths.existingRoot(projectDirectory, "[build].source", config.build().source());
        Path jarPath = classifierJarPath(projectDirectory, config, "sources");
        try {
            Files.createDirectories(jarPath.getParent());
            List<Path> files = sourceFiles(sourceRoot);
            PackageArchiveWriter.writeJarFromFiles(jarPath, sourceRoot, files);
            return new PackageArtifact("sources", jarPath, files.size());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package sources jar at "
                            + jarPath
                            + ". Check that source files are readable and target/ is writable.",
                    exception);
        }
    }

    private PackageArtifact packageJavadocJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Optional<List<ResolvedClasspathPackage>> classpathPackages,
            Optional<ClasspathSet> classpaths) {
        Path sourceRoot = ProjectPaths.existingRoot(projectDirectory, "[build].source", config.build().source());
        Path javadocDirectory = ProjectPaths.output(projectDirectory, "package javadoc output", "target/javadoc");
        Path jarPath = classifierJarPath(projectDirectory, config, "javadoc");
        try {
            Files.createDirectories(jarPath.getParent());
            deleteDirectory(javadocDirectory);
            Files.createDirectories(javadocDirectory);
            List<Path> sources = sourceFiles(sourceRoot);
            if (!sources.isEmpty()) {
                runJavadoc(
                        projectDirectory,
                        sourceRoot,
                        javadocDirectory,
                        sources,
                        javadocClasspath(buildResult, classpathPackages, classpaths));
            }
            List<Path> files = regularFiles(javadocDirectory);
            PackageArchiveWriter.writeJarFromFiles(jarPath, javadocDirectory, files);
            return new PackageArtifact("javadoc", jarPath, files.size());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package javadoc jar at "
                            + jarPath
                            + ". Check that target/ is writable and retry.",
                    exception);
        }
    }

    private static PackageArtifact packageTestJar(Path projectDirectory, ProjectConfig config) {
        Path testOutput = ProjectPaths.output(projectDirectory, "[build].testOutput", config.build().testOutput());
        Path jarPath = classifierJarPath(projectDirectory, config, "tests");
        if (!Files.isDirectory(testOutput)) {
            throw new PackageException(
                    "Cannot package test jar because compiled test output is missing at "
                            + testOutput
                            + ". Run `zolt test` first, then retry `zolt package`.");
        }
        try {
            Files.createDirectories(jarPath.getParent());
            List<Path> files = compiledFiles(testOutput);
            PackageArchiveWriter.writeJarFromFiles(jarPath, testOutput, files);
            return new PackageArtifact("tests", jarPath, files.size());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package test jar at "
                            + jarPath
                            + ". Check that compiled test output is readable and target/ is writable.",
                    exception);
        }
    }

    private List<Path> javadocClasspath(
            BuildResult buildResult,
            Optional<List<ResolvedClasspathPackage>> classpathPackages,
            Optional<ClasspathSet> classpaths) {
        List<Path> entries = new ArrayList<>();
        entries.add(buildResult.outputDirectory());
        if (classpaths.isPresent()) {
            entries.addAll(classpaths.orElseThrow().compile().entries());
        } else {
            classpathPackages
                    .map(classpathBuilder::build)
                    .ifPresent(resolvedClasspaths -> entries.addAll(resolvedClasspaths.compile().entries()));
        }
        return entries.stream().map(Path::toAbsolutePath).map(Path::normalize).toList();
    }

    private static void runJavadoc(
            Path projectDirectory,
            Path sourceRoot,
            Path outputDirectory,
            List<Path> sources,
            List<Path> classpath) {
        List<String> command = new ArrayList<>();
        command.add(javadocExecutable().toString());
        command.add("-quiet");
        command.add("-d");
        command.add(outputDirectory.toString());
        command.add("-sourcepath");
        command.add(sourceRoot.toString());
        if (!classpath.isEmpty()) {
            command.add("-classpath");
            command.add(classpath.stream()
                    .map(Path::toString)
                    .collect(Collectors.joining(java.io.File.pathSeparator)));
        }
        sources.stream().map(Path::toString).forEach(command::add);
        try {
            Process process = new ProcessBuilder(command)
                    .directory(projectDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new PackageException(
                        "javadoc failed with exit code "
                                + exitCode
                                + ". Fix Javadoc errors or disable [package].javadoc, then retry.\n"
                                + output.stripTrailing());
            }
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not run javadoc. Check that the configured JDK includes the javadoc tool.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PackageException("javadoc was interrupted. Try packaging again.", exception);
        }
    }

    private static Path javadocExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", executable("javadoc"));
    }

    private static String executable(String name) {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? name + ".exe" : name;
    }

    private static List<Path> sourceFiles(Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        try (var stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(path -> entryName(sourceRoot, path)))
                    .toList();
        }
    }

    private static List<Path> regularFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> entryName(root, path)))
                    .toList();
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.delete(path);
            }
        }
    }

    private List<PackageRuntimeJar> runtimeJars(ZoltLockfile lockfile, Path cacheRoot) {
        return runtimeJars(packagedClasspathPackages(lockfile, cacheRoot));
    }

    private List<PackageRuntimeJar> runtimeJars(List<ResolvedClasspathPackage> classpathPackages) {
        Map<String, PackageRuntimeJar> runtimeJars = new LinkedHashMap<>();
        packagedClasspathPackages(classpathPackages).stream()
                .filter(dependency -> dependency.scope().entersMainRuntimeClasspath())
                .sorted(Comparator.comparing(PackageService::classpathSortKey))
                .map(dependency -> new PackageRuntimeJar(
                        dependency.resolvedPackage().packageId(),
                        dependency.resolvedPackage().selectedVersion(),
                        dependency.resolvedPackage().jarPath()))
                .forEach(runtimeJar -> runtimeJars.putIfAbsent(runtimeJarKey(runtimeJar), runtimeJar));
        return List.copyOf(runtimeJars.values());
    }

    private List<PackageRuntimeJar> runtimeJarsWithoutProvidedDuplicates(List<ResolvedClasspathPackage> classpathPackages) {
        Set<PackageId> providedPackageIds = providedPackageIds(classpathPackages);
        return runtimeJars(classpathPackages).stream()
                .filter(runtimeJar -> !providedPackageIds.contains(runtimeJar.packageId()))
                .toList();
    }

    private List<PackageRuntimeJar> providedJars(List<ResolvedClasspathPackage> classpathPackages) {
        Map<String, PackageRuntimeJar> providedJars = new LinkedHashMap<>();
        classpathPackages.stream()
                .filter(dependency -> dependency.scope() == DependencyScope.PROVIDED)
                .sorted(Comparator.comparing(PackageService::classpathSortKey))
                .map(dependency -> new PackageRuntimeJar(
                        dependency.resolvedPackage().packageId(),
                        dependency.resolvedPackage().selectedVersion(),
                        dependency.resolvedPackage().jarPath()))
                .forEach(runtimeJar -> providedJars.putIfAbsent(runtimeJarKey(runtimeJar), runtimeJar));
        return List.copyOf(providedJars.values());
    }

    private Set<PackageId> providedPackageIds(List<ResolvedClasspathPackage> classpathPackages) {
        Set<PackageId> packageIds = new LinkedHashSet<>();
        classpathPackages.stream()
                .filter(dependency -> dependency.scope() == DependencyScope.PROVIDED)
                .map(dependency -> dependency.resolvedPackage().packageId())
                .forEach(packageIds::add);
        return Set.copyOf(packageIds);
    }

    private List<ResolvedClasspathPackage> packagedClasspathPackages(ZoltLockfile lockfile, Path cacheRoot) {
        return packagedClasspathPackages(LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot));
    }

    private List<ResolvedClasspathPackage> packagedClasspathPackages(List<ResolvedClasspathPackage> classpathPackages) {
        return classpathPackages.stream()
                .filter(dependency -> dependency.scope().packagedByDefault())
                .toList();
    }

    private static String classpathSortKey(ResolvedClasspathPackage dependency) {
        return dependency.resolvedPackage().packageId()
                + ":"
                + dependency.resolvedPackage().selectedVersion()
                + ":"
                + dependency.scope();
    }

    private static String runtimeJarKey(PackageRuntimeJar runtimeJar) {
        return runtimeJar.packageId() + ":" + runtimeJar.version() + ":" + runtimeJar.jarPath();
    }

    private static List<Path> compiledFiles(Path outputDirectory) throws IOException {
        try (var stream = Files.walk(outputDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !LOCAL_BUILD_FINGERPRINTS.contains(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> entryName(outputDirectory, path)))
                    .toList();
        }
    }

    private static String entryName(Path outputDirectory, Path file) {
        return outputDirectory.relativize(file).normalize().toString().replace('\\', '/');
    }

}
