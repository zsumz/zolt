package com.zolt.resolve;

import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.toml.ZoltTomlParser;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ResolvePackagingTestConfigs {
    private ResolvePackagingTestConfigs() {
    }

    static ProjectConfig platformConfig(URI baseUri) {
        return ProjectConfigs.withRuntimeDependencySections(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of("com.example:platform", "1.0.0"),
                Map.of(),
                Set.of("com.example:app"),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null);
    }

    static ProjectConfig platformVersionRefConfig(URI baseUri, String alias) {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                test = "%s"

                [versions]
                "%s" = "1.0.0"

                [platforms]
                "com.example:platform" = { versionRef = "%s" }

                [dependencies]
                "com.example:app" = {}
                """.formatted(baseUri, alias, alias));
    }

    static ProjectConfig testPlatformConfig(URI baseUri) {
        return ProjectConfigs.withRuntimeDependencySections(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of("com.example:platform", "1.0.0"),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of("com.example:app"),
                BuildSettings.defaults(),
                null);
    }

    static ProjectConfig runtimeProvidedConfig(URI baseUri) {
        return ProjectConfigs.withAllDependencySections(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of("com.example:runtime-tool", "1.0.0"),
                Set.of(),
                Map.of("jakarta.servlet:jakarta.servlet-api", "6.1.0"),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null,
                null,
                null);
    }

    static ProjectConfig devConfig(URI baseUri) {
        return ProjectConfigs.withAllDependencySections(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of("com.example:devtools", "1.0.0"),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null,
                null,
                null);
    }

    static ProjectConfig springBootPlatformConfig(URI baseUri) {
        return platformConfig(baseUri).withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT));
    }

    static ProjectConfig springBootWarPlatformConfig(URI baseUri) {
        return platformConfig(baseUri).withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT_WAR));
    }
}
