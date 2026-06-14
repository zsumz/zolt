package com.zolt.quarkus;

import com.zolt.framework.FrameworkPackagePlanDependency;
import com.zolt.framework.FrameworkPackagePlanRules;
import com.zolt.lockfile.LockPackage;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPaths;
import com.zolt.dependency.DependencyScope;
import java.nio.file.Path;

public final class QuarkusPackagePlanRules implements FrameworkPackagePlanRules {
    @Override
    public boolean supports(PackageMode mode) {
        return mode == PackageMode.QUARKUS;
    }

    @Override
    public FrameworkPackagePlanDependency dependency(LockPackage lockPackage) {
        boolean included = lockPackage.scope().entersMainRuntimeClasspath();
        return new FrameworkPackagePlanDependency(
                coordinate(lockPackage),
                lockPackage.version(),
                lockPackage.scope(),
                included ? "included" : "omitted",
                included ? "quarkus-runtime-lib" : omissionRule(lockPackage.scope()),
                included ? "target/quarkus-app/lib/" + nestedJarName(lockPackage) : "",
                included
                        ? "main runtime dependency for Quarkus augmentation output"
                        : "scope `" + lockPackage.scope().lockfileName() + "` does not enter Quarkus runtime packaging",
                lockPackage.policies());
    }

    @Override
    public Path archivePath(Path projectRoot, ProjectConfig config) {
        return ProjectPaths.output(projectRoot, "package archive", "target/quarkus-app/quarkus-run.jar");
    }

    @Override
    public String applicationLayout() {
        return "target/quarkus-app/app";
    }

    private static String omissionRule(DependencyScope scope) {
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
}
