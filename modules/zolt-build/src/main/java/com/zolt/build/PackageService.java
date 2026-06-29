package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.build.packageevidence.PackageEvidenceManifestWriter;
import com.zolt.build.packaging.PackageSupplementalArtifactAssembler;
import com.zolt.build.springboot.SpringBootPackageToolingPreparer;
import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class PackageService {
    private final BuildService buildService;
    private final SpringBootPackageToolingPreparer packageToolingPreparer;
    private final PackagePlanService packagePlanService;
    private final PackageEvidenceManifestWriter evidenceManifestWriter;
    private final PackagePrimaryArtifactAssembler primaryArtifactAssembler;
    private final PackageSupplementalArtifactAssembler supplementalArtifactAssembler;

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
        this.packageToolingPreparer = new SpringBootPackageToolingPreparer(resolveService, lockfileReader);
        this.packagePlanService = packagePlanService == null ? new PackagePlanService() : packagePlanService;
        this.evidenceManifestWriter = evidenceManifestWriter;
        this.primaryArtifactAssembler = new PackagePrimaryArtifactAssembler(
                manifestGenerator,
                lockfileReader,
                classpathBuilder,
                frameworkPackageAugmenter);
        this.supplementalArtifactAssembler = new PackageSupplementalArtifactAssembler(classpathBuilder);
    }

    public PackageResult packageJar(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        Path projectRoot = projectRoot(projectDirectory);
        PackageMode mode = config.packageSettings().mode();
        PackageModeSupport.ensureSupported(mode);
        preparePackageToolingIfNeeded(projectRoot, config, cacheRoot);
        BuildResultWithClasspaths buildResult = buildService.buildWithClasspaths(
                projectRoot,
                config,
                cacheRoot,
                false);
        return packageJar(projectRoot, config, buildResult, cacheRoot);
    }

    public void preparePackageToolingIfNeeded(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        packageToolingPreparer.prepareIfNeeded(projectDirectory, config, cacheRoot);
    }

    public PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path cacheRoot) {
        PackageMode mode = config.packageSettings().mode();
        PackageModeSupport.ensureSupported(mode);
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
        PackageModeSupport.ensureSupported(mode);
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
        PackageModeSupport.ensureSupported(mode);
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
        PackageModeSupport.ensureSupported(mode);
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
        PackageResult result = primaryArtifactAssembler.assemble(
                projectDirectory,
                config,
                buildResult,
                cacheRoot,
                classpathPackages,
                classpaths);
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

}
