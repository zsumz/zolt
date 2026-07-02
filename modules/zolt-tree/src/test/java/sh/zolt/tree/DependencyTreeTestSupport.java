package sh.zolt.tree;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.LockPolicyEffect;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import java.util.List;
import java.util.Map;
import java.util.Optional;

abstract class DependencyTreeTestSupport {
    protected static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }

    protected static LockPackage lockPackage(
            String groupId,
            String artifactId,
            String version,
            boolean direct,
            List<String> dependencies) {
        return new LockPackage(
                new PackageId(groupId, artifactId),
                version,
                "maven-central",
                DependencyScope.COMPILE,
                direct,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                dependencies);
    }

    protected static LockPackage lockPackage(
            String groupId,
            String artifactId,
            String version,
            boolean direct,
            List<String> dependencies,
            List<String> policies) {
        return new LockPackage(
                new PackageId(groupId, artifactId),
                version,
                "maven-central",
                DependencyScope.COMPILE,
                direct,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                dependencies,
                policies);
    }

    protected static LockPolicyEffect policyEffect() {
        return new LockPolicyEffect(
                "global-exclusion",
                new PackageId("commons-logging", "commons-logging"),
                Optional.of("1.2"),
                Optional.of("com.example:app:1.0.0"),
                "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)");
    }
}
