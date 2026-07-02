package sh.zolt.resolve;

import sh.zolt.project.FrameworkSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.QuarkusPackageMode;
import sh.zolt.project.QuarkusSettings;
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
        return sh.zolt.project.ProjectConfigs.withDependencySections(
                        new sh.zolt.project.ProjectMetadata("demo", "0.1.0", "com.example", "21", java.util.Optional.of("com.example.Main")),
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
                        sh.zolt.project.BuildSettings.defaults(),
                        null)
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
    }
}
