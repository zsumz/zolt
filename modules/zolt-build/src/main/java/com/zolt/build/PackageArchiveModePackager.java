package com.zolt.build;

import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.build.packaging.PackageRuntimeJar;
import com.zolt.build.packaging.PackageRuntimeJarSelector;
import com.zolt.build.packaging.QuarkusFastJarLayoutAssembler;
import com.zolt.build.packaging.UberJarLayoutAssembler;
import com.zolt.build.packaging.WarLayoutAssembler;
import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.framework.FrameworkPackageResult;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class PackageArchiveModePackager {
    private final ZoltLockfileReader lockfileReader;
    private final FrameworkPackageAugmenter frameworkPackageAugmenter;
    private final WarLayoutAssembler warLayoutAssembler;
    private final SpringBootJarLayoutAssembler springBootJarLayoutAssembler;
    private final SpringBootWarLayoutAssembler springBootWarLayoutAssembler;
    private final QuarkusFastJarLayoutAssembler quarkusFastJarLayoutAssembler;
    private final UberJarLayoutAssembler uberJarLayoutAssembler;
    private final PackageRuntimeJarSelector runtimeJarSelector;

    PackageArchiveModePackager(
            ManifestGenerator manifestGenerator,
            ZoltLockfileReader lockfileReader,
            FrameworkPackageAugmenter frameworkPackageAugmenter) {
        this.lockfileReader = lockfileReader;
        this.frameworkPackageAugmenter = frameworkPackageAugmenter;
        this.warLayoutAssembler = new WarLayoutAssembler(manifestGenerator);
        this.springBootJarLayoutAssembler = new SpringBootJarLayoutAssembler();
        this.springBootWarLayoutAssembler = new SpringBootWarLayoutAssembler();
        this.quarkusFastJarLayoutAssembler = new QuarkusFastJarLayoutAssembler();
        this.uberJarLayoutAssembler = new UberJarLayoutAssembler(manifestGenerator);
        this.runtimeJarSelector = new PackageRuntimeJarSelector();
    }

    PackageResult packageSpringBootJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path jarPath,
            Path cacheRoot,
            Optional<List<ResolvedClasspathPackage>> classpathPackages) {
        String startClass = config.project().main().orElseThrow(() -> new PackageException(
                "Spring Boot package mode requires [project].main in zolt.toml. Add the application main class and retry."));
        Path outputDirectory = requireOutputDirectory(buildResult);
        List<PackageRuntimeJar> runtimeJars = classpathPackages
                .map(runtimeJarSelector::runtimeJars)
                .orElseGet(() -> runtimeJarSelector.runtimeJars(
                        lockfileReader.read(projectDirectory.resolve("zolt.lock")),
                        cacheRoot));
        return springBootJarLayoutAssembler.assemble(startClass, buildResult, outputDirectory, jarPath, runtimeJars);
    }

    PackageResult packageWar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path warPath,
            Path cacheRoot,
            Optional<List<ResolvedClasspathPackage>> classpathPackages) {
        Path outputDirectory = requireOutputDirectory(buildResult);
        List<ResolvedClasspathPackage> resolvedPackages = classpathPackages
                .orElseGet(() -> runtimeJarSelector.packagedClasspathPackages(
                        lockfileReader.read(projectDirectory.resolve("zolt.lock")),
                        cacheRoot));
        List<PackageRuntimeJar> runtimeJars = runtimeJarSelector.runtimeJarsWithoutProvidedDuplicates(resolvedPackages);
        return warLayoutAssembler.assemble(config, buildResult, outputDirectory, warPath, runtimeJars);
    }

    PackageResult packageUberJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path jarPath,
            Path cacheRoot,
            Optional<List<ResolvedClasspathPackage>> classpathPackages) {
        Path outputDirectory = requireOutputDirectory(buildResult);
        List<PackageRuntimeJar> runtimeJars = classpathPackages
                .map(runtimeJarSelector::runtimeJars)
                .orElseGet(() -> runtimeJarSelector.runtimeJars(
                        lockfileReader.read(projectDirectory.resolve("zolt.lock")),
                        cacheRoot));
        return uberJarLayoutAssembler.assemble(config, buildResult, outputDirectory, jarPath, runtimeJars);
    }

    PackageResult packageSpringBootWar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path warPath,
            Path cacheRoot,
            Optional<List<ResolvedClasspathPackage>> classpathPackages) {
        String startClass = config.project().main().orElseThrow(() -> new PackageException(
                "Spring Boot WAR package mode requires [project].main in zolt.toml. Add the application main class and retry."));
        Path outputDirectory = requireOutputDirectory(buildResult);
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

    PackageResult packageFrameworkJar(
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

    private static Path requireOutputDirectory(BuildResult buildResult) {
        Path outputDirectory = buildResult.outputDirectory();
        if (!Files.isDirectory(outputDirectory)) {
            throw new PackageException(
                    "Build output directory does not exist at "
                            + outputDirectory
                            + ". Run zolt build and check [build].output in zolt.toml.");
        }
        return outputDirectory;
    }
}
