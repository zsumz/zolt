package com.zolt.resolve;

import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import java.net.URI;
import java.util.Map;

final class ResolveFeatureTestConfigs {
    private ResolveFeatureTestConfigs() {
    }

    static ProjectConfig quarkusConfigWithDependencies(URI baseUri, Map<String, String> dependencies) {
        return ResolveTestConfigs.configWithDependencies(baseUri, dependencies)
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
    }

    static ProjectConfig quarkusPlatformConfigWithDependencies(URI baseUri, Map<String, String> dependencies) {
        return com.zolt.project.ProjectConfigs.withDependencySections(
                        new com.zolt.project.ProjectMetadata("demo", "0.1.0", "com.example", "21", java.util.Optional.of("com.example.Main")),
                        Map.of("test", baseUri.toString()),
                        Map.of("io.quarkus.platform:quarkus-bom", "3.33.0"),
                        Map.of(),
                        dependencies.keySet(),
                        Map.of(),
                        java.util.Set.of(),
                        Map.of(),
                        java.util.Set.of(),
                        Map.of(),
                        java.util.Set.of(),
                        com.zolt.project.BuildSettings.defaults(),
                        null)
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
    }
}
