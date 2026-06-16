package com.zolt.resolve;

import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.toml.ZoltTomlParser;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ResolveTestConfigs {
    private ResolveTestConfigs() {
    }

    static ProjectConfig config(URI baseUri) {
        return configWithDependencies(baseUri, Map.of("com.example:app", "1.0.0"));
    }

    static ProjectConfig configWithDependencies(URI baseUri, Map<String, String> dependencies) {
        return configWithRepositoryAndDependencies(baseUri.toString(), dependencies);
    }

    static ProjectConfig configWithRepository(String repositoryUrl) {
        return configWithRepositoryAndDependencies(repositoryUrl, Map.of("com.example:app", "1.0.0"));
    }

    static ProjectConfig configWithRepositoryAndDependencies(String repositoryUrl, Map<String, String> dependencies) {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", repositoryUrl),
                dependencies,
                Map.of(),
                BuildSettings.defaults());
    }

    static ProjectConfig quarkusConfigWithDependencies(URI baseUri, Map<String, String> dependencies) {
        return configWithDependencies(baseUri, dependencies)
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
    }

    static ProjectConfig quarkusPlatformConfigWithDependencies(URI baseUri, Map<String, String> dependencies) {
        return ProjectConfigs.withDependencySections(
                        new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                        Map.of("test", baseUri.toString()),
                        Map.of("io.quarkus.platform:quarkus-bom", "3.33.0"),
                        Map.of(),
                        dependencies.keySet(),
                        Map.of(),
                        Set.of(),
                        Map.of(),
                        Set.of(),
                        Map.of(),
                        Set.of(),
                        BuildSettings.defaults(),
                        null)
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
    }

    static ProjectConfig configWithTestDependencies(URI baseUri, Map<String, String> testDependencies) {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(),
                testDependencies,
                BuildSettings.defaults());
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
        return new ProjectConfig(
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
        return new ProjectConfig(
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

    static ProjectConfig processorConfig(URI baseUri) {
        return ProjectConfigs.withDependencySections(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of("com.example:processor", "1.0.0"),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null);
    }

    static ProjectConfig openApiConfig(URI baseUri) {
        OpenApiGenerationSettings settings = new OpenApiGenerationSettings(
                Optional.of("org.openapitools:openapi-generator-cli"),
                Optional.of("7.11.0"),
                Optional.empty(),
                Optional.of("spring"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());
        GeneratedSourceStep step = new GeneratedSourceStep(
                "public-api",
                GeneratedSourceKind.OPENAPI,
                "java",
                "target/generated/sources/openapi/public-api",
                List.of("src/main/openapi/public-api.yaml"),
                true,
                true,
                settings);
        return configWithDependencies(baseUri, Map.of())
                .withBuildSettings(BuildSettings.defaults().withGeneratedSources(List.of(step), List.of()));
    }

    static ProjectConfig openApiVersionRefConfig(URI baseUri, String alias) {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                test = "%s"

                [versions]
                "%s" = "7.11.0"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                versionRef = "%s"

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                generator = "spring"
                """.formatted(baseUri, alias, alias));
    }

    static ProjectConfig configWithDependencyAndProcessor(URI baseUri) {
        return ProjectConfigs.withDependencySections(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(),
                Map.of("com.example:app", "1.0.0"),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of("com.example:processor", "1.0.0"),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null);
    }

    static ProjectConfig processorPlatformConfig(URI baseUri) {
        return ProjectConfigs.withDependencySections(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of("com.example:platform", "1.0.0"),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of("com.example:processor"),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null);
    }
}
