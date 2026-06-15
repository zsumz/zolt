package com.zolt.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ProjectConfigUpdaterTest {
    @Test
    void copyMethodsReplaceOnlyRequestedModelSection() {
        ProjectConfig base = config();
        BuildSettings build = new BuildSettings("src", "tests", "out/main", "out/test");
        PackageSettings packageSettings = new PackageSettings(PackageMode.SPRING_BOOT);
        FrameworkSettings frameworkSettings = new FrameworkSettings(
                new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR));
        DependencyPolicySettings policy = new DependencyPolicySettings(
                List.of(new DependencyPolicyExclusion("com.example", "blocked", Optional.of("test policy"))),
                Map.of());
        DependencyMetadata metadata = new DependencyMetadata(
                "dependencies",
                "com.example:lib",
                "1.0.0",
                null,
                false,
                null,
                true,
                false,
                List.of());

        assertCopy(base, base.withBuildSettings(build), build, base.packageSettings(), base.frameworkSettings(),
                base.dependencyPolicy(), base.versionAliases(), base.dependencyMetadata());
        assertCopy(base, base.withVersionAliases(Map.of("lib", "1.0.0")), base.build(), base.packageSettings(),
                base.frameworkSettings(), base.dependencyPolicy(), Map.of("lib", "1.0.0"), base.dependencyMetadata());
        assertCopy(base, base.withPackageSettings(packageSettings), base.build(), packageSettings,
                base.frameworkSettings(), base.dependencyPolicy(), base.versionAliases(), base.dependencyMetadata());
        assertCopy(base, base.withFrameworkSettings(frameworkSettings), base.build(), base.packageSettings(),
                frameworkSettings, base.dependencyPolicy(), base.versionAliases(), base.dependencyMetadata());
        assertCopy(base, base.withDependencyPolicy(policy), base.build(), base.packageSettings(),
                base.frameworkSettings(), policy, base.versionAliases(), base.dependencyMetadata());
        assertCopy(base, base.withDependencyMetadata(Map.of(DependencyMetadata.key("dependencies", "com.example:lib"), metadata)),
                base.build(), base.packageSettings(), base.frameworkSettings(), base.dependencyPolicy(),
                base.versionAliases(), Map.of(DependencyMetadata.key("dependencies", "com.example:lib"), metadata));
    }

    private static void assertCopy(
            ProjectConfig base,
            ProjectConfig updated,
            BuildSettings build,
            PackageSettings packageSettings,
            FrameworkSettings frameworkSettings,
            DependencyPolicySettings dependencyPolicy,
            Map<String, String> versionAliases,
            Map<String, DependencyMetadata> dependencyMetadata) {
        assertNotSame(base, updated);
        assertEquals(base.project(), updated.project());
        assertEquals(base.repositories(), updated.repositories());
        assertEquals(base.dependencies(), updated.dependencies());
        assertEquals(base.testDependencies(), updated.testDependencies());
        assertEquals(build, updated.build());
        assertEquals(packageSettings, updated.packageSettings());
        assertEquals(frameworkSettings, updated.frameworkSettings());
        assertEquals(dependencyPolicy, updated.dependencyPolicy());
        assertEquals(versionAliases, updated.versionAliases());
        assertEquals(dependencyMetadata, updated.dependencyMetadata());
    }

    private static ProjectConfig config() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty()),
                ProjectConfig.defaultRepositories(),
                Map.of("com.example:main", "1.0.0"),
                Map.of("com.example:test", "1.0.0"),
                BuildSettings.defaults(),
                NativeSettings.defaults());
    }
}
