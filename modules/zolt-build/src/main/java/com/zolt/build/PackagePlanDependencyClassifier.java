package com.zolt.build;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.framework.FrameworkPackagePlanDependency;
import com.zolt.framework.FrameworkPackagePlanRules;
import com.zolt.lockfile.LockPackage;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

final class PackagePlanDependencyClassifier {
    private static final PackageId SPRING_BOOT_LOADER_PACKAGE = new PackageId(
            "org.springframework.boot",
            "spring-boot-loader");

    private PackagePlanDependencyClassifier() {}

    static PackagePlanDependency dependency(
            PackageMode mode,
            LockPackage lockPackage,
            Set<PackageId> providedPackageIds,
            Optional<FrameworkPackagePlanRules> packagePlanRules,
            ProjectConfig config) {
        String nestedJar = nestedJarName(lockPackage);
        return switch (mode) {
            case THIN -> thinDependency(lockPackage);
            case SPRING_BOOT -> springBootDependency(lockPackage, nestedJar);
            case WAR -> warDependency(lockPackage, nestedJar, providedPackageIds);
            case SPRING_BOOT_WAR -> springBootWarDependency(lockPackage, nestedJar, providedPackageIds);
            case QUARKUS -> packagePlanRules
                    .map(rules -> dependency(rules.dependency(lockPackage, config)))
                    .orElseGet(() -> unsupportedFrameworkDependency(mode, lockPackage));
            case UBER -> uberDependency(lockPackage);
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
                included ? "thin-runtime-classpath" : PackagePlanDependencyOmissions.rule(lockPackage.scope(), false),
                included ? "runtime-classpath sidecar" : "",
                included
                        ? "dependency remains outside the thin jar and is written to the runtime classpath sidecar"
                        : PackagePlanDependencyOmissions.reason(lockPackage.scope(), false),
                lockPackage.policies());
    }

    private static PackagePlanDependency uberDependency(LockPackage lockPackage) {
        boolean included = lockPackage.scope().packagedByDefault();
        return new PackagePlanDependency(
                coordinate(lockPackage),
                lockPackage.version(),
                lockPackage.scope(),
                included ? "included" : "omitted",
                included ? "uber-runtime-merged" : PackagePlanDependencyOmissions.rule(lockPackage.scope(), false),
                included ? "archive root" : "",
                included
                        ? "runtime dependency classes and resources are merged into the uber jar"
                        : PackagePlanDependencyOmissions.reason(lockPackage.scope(), false),
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
                included ? "spring-boot-runtime-nested" : PackagePlanDependencyOmissions.rule(lockPackage.scope(), false),
                included ? "BOOT-INF/lib/" + nestedJar : "",
                included
                        ? "runtime dependency packaged as a nested Spring Boot jar"
                        : PackagePlanDependencyOmissions.reason(lockPackage.scope(), false),
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
                included ? "war-runtime-lib" : PackagePlanDependencyOmissions.rule(lockPackage.scope(), false),
                included ? "WEB-INF/lib/" + nestedJar : "",
                included
                        ? "runtime dependency packaged for the servlet container"
                        : PackagePlanDependencyOmissions.reason(lockPackage.scope(), false),
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
                included ? "spring-boot-war-runtime-lib" : PackagePlanDependencyOmissions.rule(lockPackage.scope(), true),
                included ? "WEB-INF/lib/" + nestedJar : "",
                included
                        ? "runtime dependency packaged for the Spring Boot WAR launcher"
                        : PackagePlanDependencyOmissions.reason(lockPackage.scope(), false),
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

    private static String nestedJarName(LockPackage lockPackage) {
        return lockPackage.jar()
                .map(Path::of)
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> lockPackage.packageId().toString().replace(':', '-') + "-" + lockPackage.version() + ".jar");
    }

    private static String coordinate(LockPackage lockPackage) {
        return classifier(lockPackage)
                .map(classifier -> lockPackage.packageId().groupId()
                        + ":"
                        + lockPackage.packageId().artifactId()
                        + ":"
                        + classifier
                        + ":jar:"
                        + lockPackage.version())
                .orElseGet(() -> lockPackage.packageId() + ":" + lockPackage.version());
    }

    private static Optional<String> classifier(LockPackage lockPackage) {
        return lockPackage.jar()
                .map(Path::of)
                .map(Path::getFileName)
                .map(Path::toString)
                .flatMap(fileName -> classifierFromJarName(
                        lockPackage.packageId().artifactId(),
                        lockPackage.version(),
                        fileName));
    }

    private static Optional<String> classifierFromJarName(String artifactId, String version, String fileName) {
        String prefix = artifactId + "-" + version + "-";
        String suffix = ".jar";
        if (!fileName.startsWith(prefix) || !fileName.endsWith(suffix)) {
            return Optional.empty();
        }
        String classifier = fileName.substring(prefix.length(), fileName.length() - suffix.length());
        return classifier.isBlank() ? Optional.empty() : Optional.of(classifier);
    }
}
