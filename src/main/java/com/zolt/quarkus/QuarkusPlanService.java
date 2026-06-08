package com.zolt.quarkus;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class QuarkusPlanService {
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final QuarkusExtensionMetadataReader metadataReader;
    private final QuarkusInputFingerprint inputFingerprint;
    private final QuarkusAugmentationStateReader augmentationStateReader;

    public QuarkusPlanService() {
        this(
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new QuarkusExtensionMetadataReader(),
                new QuarkusInputFingerprint(),
                new QuarkusAugmentationStateReader());
    }

    QuarkusPlanService(
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            QuarkusExtensionMetadataReader metadataReader,
            QuarkusInputFingerprint inputFingerprint,
            QuarkusAugmentationStateReader augmentationStateReader) {
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.metadataReader = metadataReader;
        this.inputFingerprint = inputFingerprint;
        this.augmentationStateReader = augmentationStateReader;
    }

    public QuarkusPlan plan(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        requireEnabled(config);
        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        return plan(projectDirectory, config, lockfile, cacheRoot);
    }

    public QuarkusPlan plan(
            Path projectDirectory,
            ProjectConfig config,
            ZoltLockfile lockfile,
            Path cacheRoot) {
        requireEnabled(config);
        Path root = projectDirectory.toAbsolutePath().normalize();
        Path cache = cacheRoot.toAbsolutePath().normalize();
        Path applicationClasses = root.resolve(config.build().output()).normalize();
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cache));
        String fingerprint = inputFingerprint.fingerprint(applicationClasses, lockfile);
        return new QuarkusPlan(
                root,
                applicationClasses,
                config.frameworkSettings().quarkus().packageMode(),
                outputLayout(root),
                new QuarkusApplicationArtifact(
                        new PackageId(config.project().group(), config.project().name()),
                        config.project().version(),
                        applicationClasses),
                fingerprint,
                augmentationStateReader.read(root, fingerprint),
                classpaths.runtime().entries(),
                classpaths.quarkusDeployment().entries(),
                platformPropertiesArtifacts(lockfile, cache),
                bootstrapDependencies(lockfile, cache),
                extensions(lockfile, cache));
    }

    private static QuarkusOutputLayout outputLayout(Path projectRoot) {
        return QuarkusOutputLayout.forProject(projectRoot);
    }

    private static void requireEnabled(ProjectConfig config) {
        if (!config.frameworkSettings().quarkus().enabled()) {
            throw new QuarkusPlanException(
                    "Quarkus is not enabled for this project. "
                            + "Add `[framework.quarkus] enabled = true` to zolt.toml, "
                            + "run `zolt resolve`, then run `zolt quarkus plan` again.");
        }
    }

    private List<QuarkusPlanExtension> extensions(ZoltLockfile lockfile, Path cacheRoot) {
        Map<String, LockPackage> deploymentPackages = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.scope() == DependencyScope.QUARKUS_DEPLOYMENT)
                .collect(Collectors.toMap(
                        QuarkusPlanService::deploymentKey,
                        Function.identity(),
                        (left, ignored) -> left));
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.jar().isPresent())
                .filter(lockPackage -> lockPackage.scope().entersMainRuntimeClasspath())
                .flatMap(lockPackage -> extension(lockPackage, cacheRoot, deploymentPackages).stream())
                .sorted(Comparator.comparing(extension -> extension.runtimePackage().toString()))
                .toList();
    }

    private static List<QuarkusBootstrapDependency> bootstrapDependencies(ZoltLockfile lockfile, Path cacheRoot) {
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.jar().isPresent())
                .filter(lockPackage -> lockPackage.scope().entersMainRuntimeClasspath()
                        || lockPackage.scope() == DependencyScope.QUARKUS_DEPLOYMENT)
                .map(lockPackage -> new QuarkusBootstrapDependency(
                        lockPackage.packageId(),
                        lockPackage.version(),
                        lockPackage.scope(),
                        cacheRoot.resolve(lockPackage.jar().orElseThrow()),
                        lockPackage.direct()))
                .sorted(Comparator.comparing(QuarkusPlanService::bootstrapDependencyKey))
                .toList();
    }

    private static List<QuarkusPlatformPropertiesArtifact> platformPropertiesArtifacts(
            ZoltLockfile lockfile,
            Path cacheRoot) {
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.scope() == DependencyScope.QUARKUS_DEPLOYMENT)
                .filter(lockPackage -> lockPackage.artifact().isPresent())
                .filter(lockPackage -> lockPackage.artifactType().filter("properties"::equals).isPresent())
                .map(lockPackage -> new QuarkusPlatformPropertiesArtifact(
                        lockPackage.packageId(),
                        lockPackage.version(),
                        cacheRoot.resolve(lockPackage.artifact().orElseThrow())))
                .sorted(Comparator.comparing(artifact ->
                        artifact.packageId() + ":" + artifact.version() + ":" + artifact.path()))
                .toList();
    }

    private static String bootstrapDependencyKey(QuarkusBootstrapDependency dependency) {
        return dependency.scope().lockfileName()
                + ":"
                + dependency.packageId()
                + ":"
                + dependency.version()
                + ":"
                + dependency.path();
    }

    private Optional<QuarkusPlanExtension> extension(
            LockPackage runtimePackage,
            Path cacheRoot,
            Map<String, LockPackage> deploymentPackages) {
        Path runtimePath = cacheRoot.resolve(runtimePackage.jar().orElseThrow());
        if (!Files.isRegularFile(runtimePath)) {
            return Optional.empty();
        }
        Optional<QuarkusExtensionMetadata> metadata = metadata(runtimePath);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        QuarkusDeploymentArtifact deploymentArtifact = metadata.orElseThrow().deploymentArtifact();
        Optional<Path> deploymentPath = Optional.ofNullable(deploymentPackages.get(deploymentKey(deploymentArtifact)))
                .flatMap(LockPackage::jar)
                .map(cacheRoot::resolve);
        return Optional.of(new QuarkusPlanExtension(
                runtimePackage.packageId(),
                runtimePath,
                deploymentArtifact,
                deploymentPath));
    }

    private Optional<QuarkusExtensionMetadata> metadata(Path runtimePath) {
        try {
            return metadataReader.readIfPresent(runtimePath);
        } catch (QuarkusMetadataException exception) {
            throw new QuarkusPlanException(exception.getMessage());
        }
    }

    private static String deploymentKey(LockPackage lockPackage) {
        return deploymentKey(lockPackage.packageId(), lockPackage.version());
    }

    private static String deploymentKey(QuarkusDeploymentArtifact artifact) {
        return deploymentKey(new PackageId(artifact.groupId(), artifact.artifactId()), artifact.version());
    }

    private static String deploymentKey(PackageId packageId, String version) {
        return packageId + ":" + version;
    }
}
