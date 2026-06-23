package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class PackagePrimaryArtifactAssembler {
    private final ThinJarLayoutAssembler thinJarLayoutAssembler;
    private final PackageArchiveModePackager archiveModePackager;
    private final PackageArtifactPathPlanner artifactPathPlanner;

    PackagePrimaryArtifactAssembler(
            ManifestGenerator manifestGenerator,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this.thinJarLayoutAssembler = new ThinJarLayoutAssembler(
                manifestGenerator,
                lockfileReader,
                classpathBuilder);
        this.archiveModePackager = new PackageArchiveModePackager(
                manifestGenerator,
                lockfileReader,
                frameworkPackageAugmenter);
        this.artifactPathPlanner = new PackageArtifactPathPlanner();
    }

    PackageResult assemble(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Optional<Path> cacheRoot,
            Optional<List<ResolvedClasspathPackage>> classpathPackages,
            Optional<ClasspathSet> classpaths) {
        PackageMode mode = config.packageSettings().mode();
        return switch (mode) {
            case THIN -> thinJarLayoutAssembler.assemble(
                    projectDirectory,
                    config,
                    buildResult,
                    artifactPathPlanner.jarPath(projectDirectory, config),
                    cacheRoot,
                    classpathPackages,
                    classpaths);
            case SPRING_BOOT -> archiveModePackager.packageSpringBootJar(
                    projectDirectory,
                    config,
                    buildResult,
                    artifactPathPlanner.jarPath(projectDirectory, config),
                    requiredCacheRoot(
                            cacheRoot,
                            "Spring Boot package mode requires dependency jar access from zolt.lock. Use single-project `zolt package --mode spring-boot` for now; workspace Spring Boot packaging is not wired yet."),
                    classpathPackages);
            case WAR -> archiveModePackager.packageWar(
                    projectDirectory,
                    config,
                    buildResult,
                    artifactPathPlanner.archivePath(projectDirectory, config, "war"),
                    requiredCacheRoot(
                            cacheRoot,
                            "WAR package mode requires dependency jar access from zolt.lock. Use single-project `zolt package --mode war` for now; workspace WAR packaging is not wired yet."),
                    classpathPackages);
            case SPRING_BOOT_WAR -> archiveModePackager.packageSpringBootWar(
                    projectDirectory,
                    config,
                    buildResult,
                    artifactPathPlanner.archivePath(projectDirectory, config, "war"),
                    requiredCacheRoot(
                            cacheRoot,
                            "Spring Boot WAR package mode requires dependency jar access from zolt.lock. Use single-project `zolt package --mode spring-boot-war` for now; workspace Spring Boot WAR packaging is not wired yet."),
                    classpathPackages);
            case QUARKUS -> archiveModePackager.packageFrameworkJar(
                    projectDirectory,
                    config,
                    buildResult,
                    mode,
                    requiredCacheRoot(
                            cacheRoot,
                            "Framework package mode `"
                                    + mode.configValue()
                                    + "` requires dependency jar access from zolt.lock. Use single-project `zolt package --mode "
                                    + mode.configValue()
                                    + "` for now; workspace framework packaging is not wired yet."));
            case UBER -> archiveModePackager.packageUberJar(
                    projectDirectory,
                    config,
                    buildResult,
                    artifactPathPlanner.jarPath(projectDirectory, config),
                    requiredCacheRoot(
                            cacheRoot,
                            "Uber package mode requires dependency jar access from zolt.lock. Use single-project `zolt package --mode uber` for now; workspace uber packaging is not wired yet."),
                    classpathPackages);
        };
    }

    private static Path requiredCacheRoot(Optional<Path> cacheRoot, String message) {
        return cacheRoot.orElseThrow(() -> new PackageException(message));
    }
}
