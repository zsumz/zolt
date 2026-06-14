package com.zolt.build;

import com.zolt.framework.FrameworkPackagePlanDependency;
import com.zolt.framework.FrameworkPackagePlanRules;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPaths;
import com.zolt.dependency.PackageId;
import com.zolt.resolve.DependencyScope;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class PackagePlanService {
    private static final PackageId SPRING_BOOT_LOADER_PACKAGE = new PackageId(
            "org.springframework.boot",
            "spring-boot-loader");

    private final ZoltLockfileReader lockfileReader;
    private final List<FrameworkPackagePlanRules> packagePlanRules;

    public PackagePlanService() {
        this(List.of());
    }

    public PackagePlanService(List<FrameworkPackagePlanRules> packagePlanRules) {
        this(new ZoltLockfileReader(), packagePlanRules);
    }

    PackagePlanService(ZoltLockfileReader lockfileReader) {
        this(lockfileReader, List.of());
    }

    PackagePlanService(ZoltLockfileReader lockfileReader, List<FrameworkPackagePlanRules> packagePlanRules) {
        this.lockfileReader = lockfileReader;
        this.packagePlanRules = packagePlanRules == null ? List.of() : List.copyOf(packagePlanRules);
    }

    public PackagePlan plan(Path projectDirectory, ProjectConfig config) {
        Path projectRoot = projectRoot(projectDirectory);
        return plan(projectRoot, config, projectRoot.resolve("zolt.lock"));
    }

    public PackagePlan plan(Path projectDirectory, ProjectConfig config, Path lockfilePath) {
        Path projectRoot = projectRoot(projectDirectory);
        ZoltLockfile lockfile = lockfileReader.read(lockfilePath.toAbsolutePath().normalize());
        PackageMode mode = config.packageSettings().mode();
        Set<PackageId> providedPackageIds = providedPackageIds(lockfile);
        Optional<FrameworkPackagePlanRules> modeRules = packagePlanRules(mode);
        List<PackagePlanDependency> dependencies = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.jar().isPresent())
                .sorted(Comparator.comparing(PackagePlanService::sortKey))
                .map(lockPackage -> dependency(mode, lockPackage, providedPackageIds, modeRules))
                .toList();
        return new PackagePlan(
                projectRoot,
                mode,
                archivePath(projectRoot, config, mode, modeRules),
                projectRoot.resolve(config.build().output()).normalize(),
                applicationLayout(mode, modeRules),
                runtimeClasspathPath(projectRoot, config, mode),
                dependencies,
                warnings(mode, dependencies));
    }

    private Optional<FrameworkPackagePlanRules> packagePlanRules(PackageMode mode) {
        return packagePlanRules.stream()
                .filter(rules -> rules.supports(mode))
                .findFirst();
    }

    private static Set<PackageId> providedPackageIds(ZoltLockfile lockfile) {
        Set<PackageId> packageIds = new LinkedHashSet<>();
        for (LockPackage lockPackage : lockfile.packages()) {
            if (lockPackage.scope() == DependencyScope.PROVIDED && lockPackage.direct()) {
                packageIds.add(lockPackage.packageId());
            }
        }
        return Set.copyOf(packageIds);
    }

    private static PackagePlanDependency dependency(
            PackageMode mode,
            LockPackage lockPackage,
            Set<PackageId> providedPackageIds,
            Optional<FrameworkPackagePlanRules> packagePlanRules) {
        String nestedJar = nestedJarName(lockPackage);
        return switch (mode) {
            case THIN -> thinDependency(lockPackage);
            case SPRING_BOOT -> springBootDependency(lockPackage, nestedJar);
            case WAR -> warDependency(lockPackage, nestedJar, providedPackageIds);
            case SPRING_BOOT_WAR -> springBootWarDependency(lockPackage, nestedJar, providedPackageIds);
            case QUARKUS -> packagePlanRules
                    .map(rules -> dependency(rules.dependency(lockPackage)))
                    .orElseGet(() -> unsupportedFrameworkDependency(mode, lockPackage));
            case UBER -> new PackagePlanDependency(
                    coordinate(lockPackage),
                    lockPackage.version(),
                    lockPackage.scope(),
                    "unsupported",
                    "uber-unsupported",
                    "",
                    "package mode `uber` is not implemented yet",
                    lockPackage.policies());
        };
    }

    private static PackagePlanDependency dependency(FrameworkPackagePlanDependency dependency) {
        return new PackagePlanDependency(
                dependency.coordinate(),
                dependency.version(),
                dependency.scope(),
                dependency.lanes(),
                dependency.packageDefault(),
                dependency.laneDisposition(),
                dependency.disposition(),
                dependency.ruleName(),
                dependency.location(),
                dependency.reason(),
                dependency.policies());
    }

    private static PackagePlanDependency unsupportedFrameworkDependency(PackageMode mode, LockPackage lockPackage) {
        return new PackagePlanDependency(
                coordinate(lockPackage),
                lockPackage.version(),
                lockPackage.scope(),
                "unsupported",
                "framework-package-plan-rules-missing",
                "",
                "package mode `" + mode.configValue() + "` requires framework package plan rules",
                lockPackage.policies());
    }

    private static PackagePlanDependency thinDependency(LockPackage lockPackage) {
        boolean included = lockPackage.scope().packagedByDefault();
        return new PackagePlanDependency(
                coordinate(lockPackage),
                lockPackage.version(),
                lockPackage.scope(),
                included ? "runtime-classpath" : "omitted",
                included ? "thin-runtime-classpath" : omissionRule(lockPackage.scope(), false),
                included ? "runtime-classpath sidecar" : "",
                included
                        ? "dependency remains outside the thin jar and is written to the runtime classpath sidecar"
                        : omissionReason(lockPackage.scope(), false),
                lockPackage.policies());
    }

    private static PackagePlanDependency springBootDependency(LockPackage lockPackage, String nestedJar) {
        if (lockPackage.packageId().equals(SPRING_BOOT_LOADER_PACKAGE)) {
            return new PackagePlanDependency(
                    coordinate(lockPackage),
                    lockPackage.version(),
                    lockPackage.scope(),
                    "loader",
                    "spring-boot-loader-expanded",
                    "archive root",
                    "Spring Boot loader classes are expanded at the archive root",
                    lockPackage.policies());
        }
        boolean included = lockPackage.scope().packagedByDefault();
        return new PackagePlanDependency(
                coordinate(lockPackage),
                lockPackage.version(),
                lockPackage.scope(),
                included ? "included" : "omitted",
                included ? "spring-boot-runtime-nested" : omissionRule(lockPackage.scope(), false),
                included ? "BOOT-INF/lib/" + nestedJar : "",
                included
                        ? "runtime dependency packaged as a nested Spring Boot jar"
                        : omissionReason(lockPackage.scope(), false),
                lockPackage.policies());
    }

    private static PackagePlanDependency warDependency(
            LockPackage lockPackage,
            String nestedJar,
            Set<PackageId> providedPackageIds) {
        if (isProvidedCoordinateOverride(lockPackage, providedPackageIds)) {
            return providedCoordinateOverride(lockPackage, false);
        }
        boolean included = lockPackage.scope().packagedByDefault();
        return new PackagePlanDependency(
                coordinate(lockPackage),
                lockPackage.version(),
                lockPackage.scope(),
                included ? "included" : "omitted",
                included ? "war-runtime-lib" : omissionRule(lockPackage.scope(), false),
                included ? "WEB-INF/lib/" + nestedJar : "",
                included
                        ? "runtime dependency packaged for the servlet container"
                        : omissionReason(lockPackage.scope(), false),
                lockPackage.policies());
    }

    private static PackagePlanDependency springBootWarDependency(
            LockPackage lockPackage,
            String nestedJar,
            Set<PackageId> providedPackageIds) {
        if (lockPackage.packageId().equals(SPRING_BOOT_LOADER_PACKAGE)) {
            return new PackagePlanDependency(
                    coordinate(lockPackage),
                    lockPackage.version(),
                    lockPackage.scope(),
                    "loader",
                    "spring-boot-war-loader-expanded",
                    "archive root",
                    "Spring Boot WAR launcher classes are expanded at the archive root",
                    lockPackage.policies());
        }
        if (lockPackage.scope() == DependencyScope.PROVIDED) {
            return new PackagePlanDependency(
                    coordinate(lockPackage),
                    lockPackage.version(),
                    lockPackage.scope(),
                    "provided",
                    "spring-boot-war-provided-lib",
                    "WEB-INF/lib-provided/" + nestedJar,
                    "provided dependency is available to java -jar without entering servlet container WEB-INF/lib",
                    lockPackage.policies());
        }
        if (isProvidedCoordinateOverride(lockPackage, providedPackageIds)) {
            return providedCoordinateOverride(lockPackage, true);
        }
        boolean included = lockPackage.scope().packagedByDefault();
        return new PackagePlanDependency(
                coordinate(lockPackage),
                lockPackage.version(),
                lockPackage.scope(),
                included ? "included" : "omitted",
                included ? "spring-boot-war-runtime-lib" : omissionRule(lockPackage.scope(), true),
                included ? "WEB-INF/lib/" + nestedJar : "",
                included
                        ? "runtime dependency packaged for the Spring Boot WAR launcher"
                        : omissionReason(lockPackage.scope(), false),
                lockPackage.policies());
    }

    private static boolean isProvidedCoordinateOverride(
            LockPackage lockPackage,
            Set<PackageId> providedPackageIds) {
        return lockPackage.scope() != DependencyScope.PROVIDED
                && providedPackageIds.contains(lockPackage.packageId());
    }

    private static PackagePlanDependency providedCoordinateOverride(
            LockPackage lockPackage,
            boolean springBootWar) {
        return new PackagePlanDependency(
                coordinate(lockPackage),
                lockPackage.version(),
                lockPackage.scope(),
                "omitted",
                springBootWar ? "spring-boot-war-provided-coordinate-override" : "war-provided-coordinate-override",
                "",
                "same coordinate is declared in [provided.dependencies], so this runtime path is omitted from the deployable runtime lib directory",
                lockPackage.policies());
    }

    private static List<PackagePlanWarning> warnings(
            PackageMode mode,
            List<PackagePlanDependency> dependencies) {
        if (mode != PackageMode.WAR && mode != PackageMode.SPRING_BOOT_WAR) {
            return List.of();
        }
        List<PackagePlanWarning> warnings = new ArrayList<>();
        for (PackagePlanDependency dependency : dependencies) {
            if (!("included".equals(dependency.disposition()))
                    || !isContainerDependency(dependency.coordinate())) {
                continue;
            }
            warnings.add(new PackagePlanWarning(
                    "CONTAINER_DEPENDENCY_PACKAGED",
                    dependency.coordinate(),
                    dependency.ruleName(),
                    "Container-style dependency `" + dependency.coordinate() + "` is packaged in "
                            + dependency.location()
                            + " by package rule `"
                            + dependency.ruleName()
                            + "`"
                            + ".",
                    "Move it to [provided.dependencies] when the servlet container supplies it, then run `zolt resolve`."));
        }
        return List.copyOf(warnings);
    }

    private static boolean isContainerDependency(String coordinate) {
        return coordinate.startsWith("jakarta.servlet:")
                || coordinate.startsWith("javax.servlet:")
                || coordinate.startsWith("org.apache.tomcat:")
                || coordinate.startsWith("org.apache.tomcat.embed:")
                || coordinate.contains(":tomcat-embed-");
    }

    private static String omissionReason(DependencyScope scope, boolean springBootWar) {
        if (scope == DependencyScope.PROVIDED && springBootWar) {
            return "provided dependency is placed in WEB-INF/lib-provided";
        }
        return switch (scope) {
            case PROVIDED -> "provided dependency is expected from the servlet/container runtime";
            case DEV -> "dev dependency is excluded from package artifacts";
            case TEST -> "test dependency is excluded from main package artifacts";
            case PROCESSOR, TEST_PROCESSOR -> "annotation processor dependency is excluded from package artifacts";
            case QUARKUS_DEPLOYMENT -> "Quarkus deployment dependency is build-time tooling, not package runtime";
            case TOOL_OPENAPI -> "OpenAPI generator dependency is build-time tooling, not package runtime";
            case TOOL_COVERAGE -> "coverage dependency is build-time tooling, not package runtime";
            case COMPILE, RUNTIME -> "dependency scope is not packaged by this mode";
        };
    }

    private static String omissionRule(DependencyScope scope, boolean springBootWar) {
        if (scope == DependencyScope.PROVIDED && springBootWar) {
            return "spring-boot-war-provided-lib";
        }
        return switch (scope) {
            case PROVIDED -> "provided-container-omitted";
            case DEV -> "dev-only-omitted";
            case TEST -> "test-omitted";
            case PROCESSOR, TEST_PROCESSOR -> "processor-omitted";
            case QUARKUS_DEPLOYMENT -> "quarkus-deployment-omitted";
            case TOOL_OPENAPI -> "openapi-tool-omitted";
            case TOOL_COVERAGE -> "coverage-tool-omitted";
            case COMPILE, RUNTIME -> "non-runtime-omitted";
        };
    }

    private static Path archivePath(
            Path projectRoot,
            ProjectConfig config,
            PackageMode mode,
            Optional<FrameworkPackagePlanRules> packagePlanRules) {
        if (packagePlanRules.isPresent()) {
            return packagePlanRules.orElseThrow().archivePath(projectRoot, config);
        }
        return switch (mode) {
            case WAR, SPRING_BOOT_WAR -> ProjectPaths.output(
                    projectRoot,
                    "package archive",
                    "target/" + artifactBaseName(config) + ".war");
            case QUARKUS -> ProjectPaths.output(
                    projectRoot,
                    "package archive",
                    "target/" + artifactBaseName(config) + ".jar");
            default -> ProjectPaths.output(
                    projectRoot,
                    "package archive",
                    "target/" + artifactBaseName(config) + ".jar");
        };
    }

    private static Optional<Path> runtimeClasspathPath(Path projectRoot, ProjectConfig config, PackageMode mode) {
        if (mode != PackageMode.THIN) {
            return Optional.empty();
        }
        return Optional.of(ProjectPaths.output(
                projectRoot,
                "package runtime classpath",
                "target/" + artifactBaseName(config) + ".runtime-classpath"));
    }

    private static String artifactBaseName(ProjectConfig config) {
        return ProjectPaths.filenameComponent("[project].name", config.project().name())
                + "-"
                + ProjectPaths.filenameComponent("[project].version", config.project().version());
    }

    private static String applicationLayout(PackageMode mode, Optional<FrameworkPackagePlanRules> packagePlanRules) {
        if (packagePlanRules.isPresent()) {
            return packagePlanRules.orElseThrow().applicationLayout();
        }
        return switch (mode) {
            case THIN, UBER -> "archive root";
            case SPRING_BOOT -> "BOOT-INF/classes";
            case WAR, SPRING_BOOT_WAR -> "WEB-INF/classes";
            case QUARKUS -> "framework package output";
        };
    }

    private static String nestedJarName(LockPackage lockPackage) {
        return lockPackage.jar()
                .map(Path::of)
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> lockPackage.packageId().toString().replace(':', '-') + "-" + lockPackage.version() + ".jar");
    }

    private static String coordinate(LockPackage lockPackage) {
        return lockPackage.packageId() + ":" + lockPackage.version();
    }

    private static String sortKey(LockPackage lockPackage) {
        return lockPackage.packageId()
                + ":"
                + lockPackage.version()
                + ":"
                + lockPackage.scope().lockfileName();
    }

    private static Path projectRoot(Path projectDirectory) {
        return projectDirectory.toAbsolutePath().normalize();
    }
}
