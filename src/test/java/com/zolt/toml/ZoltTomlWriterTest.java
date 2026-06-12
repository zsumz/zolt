package com.zolt.toml;

import static com.zolt.toml.ProjectConfigFixture.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyConstraintKind;
import com.zolt.project.DependencyExclusionSpec;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicyExclusion;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.DependencySection;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.NativeSettings;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.PublicationMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.RepositorySettings;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceMissingTokenPolicy;
import com.zolt.project.ResourceTokenSettings;
import com.zolt.project.TestRuntimeSettings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ZoltTomlWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void writesDefaultConfig() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");

        String toml = writer.write(config);

        assertEquals("""
                [project]
                name = "hello"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                main = "com.example.Main"

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [platforms]

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """, toml);
    }

    @Test
    void mutationHelpersRejectUnsupportedLiteralVersions() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");

        IllegalArgumentException dependency = assertThrows(
                IllegalArgumentException.class,
                () -> writer.addDependency(
                        config,
                        DependencySection.MAIN,
                        "com.example:legacy-api",
                        "1.0-SNAPSHOT"));
        IllegalArgumentException platform = assertThrows(
                IllegalArgumentException.class,
                () -> writer.addPlatform(config, "com.example:platform", "LATEST"));

        assertTrue(dependency.getMessage().contains("Invalid external dependency version `1.0-SNAPSHOT`"));
        assertTrue(platform.getMessage().contains("Invalid platform version `LATEST`"));
    }

    @Test
    void writtenConfigParsesBackIntoModel() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        ProjectConfig parsed = parser.parse(writer.write(original));

        assertEquals(original.project(), parsed.project());
        assertEquals(original.repositories(), parsed.repositories());
        assertEquals(original.versionAliases(), parsed.versionAliases());
        assertEquals(original.platforms(), parsed.platforms());
        assertEquals(original.apiDependencies(), parsed.apiDependencies());
        assertEquals(original.managedApiDependencies(), parsed.managedApiDependencies());
        assertEquals(original.workspaceApiDependencies(), parsed.workspaceApiDependencies());
        assertEquals(original.dependencies(), parsed.dependencies());
        assertEquals(original.managedDependencies(), parsed.managedDependencies());
        assertEquals(original.workspaceDependencies(), parsed.workspaceDependencies());
        assertEquals(original.testDependencies(), parsed.testDependencies());
        assertEquals(original.managedTestDependencies(), parsed.managedTestDependencies());
        assertEquals(original.workspaceTestDependencies(), parsed.workspaceTestDependencies());
        assertEquals(original.annotationProcessors(), parsed.annotationProcessors());
        assertEquals(original.managedAnnotationProcessors(), parsed.managedAnnotationProcessors());
        assertEquals(original.testAnnotationProcessors(), parsed.testAnnotationProcessors());
        assertEquals(original.managedTestAnnotationProcessors(), parsed.managedTestAnnotationProcessors());
        assertEquals(original.dependencyPolicy(), parsed.dependencyPolicy());
        assertEquals(original.build(), parsed.build());
        assertEquals(original.compilerSettings(), parsed.compilerSettings());
        assertEquals(original.packageSettings(), parsed.packageSettings());
    }

    @Test
    void writesVersionAliasesWhenPresent() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "aliases"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                boot = "4.0.6"
                slf4j = "2.0.17"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = { versionRef = "boot" }

                [dependencies]
                "org.slf4j:slf4j-api" = { versionRef = "slf4j" }
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[versions]\n\"boot\" = \"4.0.6\"\n\"slf4j\" = \"2.0.17\""));
        assertTrue(toml.contains("\"org.springframework.boot:spring-boot-dependencies\" = { versionRef = \"boot\" }"));
        assertTrue(toml.contains("\"org.slf4j:slf4j-api\" = { versionRef = \"slf4j\" }"));
        assertEquals(Map.of("boot", "4.0.6", "slf4j", "2.0.17"), parsed.versionAliases());
        assertEquals("4.0.6", parsed.platforms().get("org.springframework.boot:spring-boot-dependencies"));
        assertEquals(
                "boot",
                parsed.dependencyMetadata()
                        .get(DependencyMetadata.key(
                                "platforms",
                                "org.springframework.boot:spring-boot-dependencies"))
                        .versionRef());
        assertEquals("2.0.17", parsed.dependencies().get("org.slf4j:slf4j-api"));
    }

    @Test
    void addsVersionRefPlatformWhileKeepingConcreteResolverInput() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "aliases"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                boot = "4.0.6"
                """);

        config = writer.addVersionRefPlatform(
                config,
                "org.springframework.boot:spring-boot-dependencies",
                "boot",
                "4.0.6");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"org.springframework.boot:spring-boot-dependencies\" = { versionRef = \"boot\" }"));
        assertEquals("4.0.6", parsed.platforms().get("org.springframework.boot:spring-boot-dependencies"));
        assertEquals(
                "boot",
                parsed.dependencyMetadata()
                        .get(DependencyMetadata.key(
                                "platforms",
                                "org.springframework.boot:spring-boot-dependencies"))
                        .versionRef());
    }

    @Test
    void addsVersionRefDependencyWhileKeepingConcreteResolverInput() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "aliases"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                guava = "33.4.8-jre"
                """);

        config = writer.addVersionRefDependency(
                config,
                DependencySection.MAIN,
                "com.google.guava:guava",
                "guava",
                "33.4.8-jre");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"com.google.guava:guava\" = { versionRef = \"guava\" }"));
        assertEquals("33.4.8-jre", parsed.dependencies().get("com.google.guava:guava"));
        assertEquals(
                "guava",
                parsed.dependencyMetadata()
                        .get(DependencyMetadata.key("dependencies", "com.google.guava:guava"))
                        .versionRef());
    }

    @Test
    void writesCredentialedRepositories() {
        ProjectConfig config = config()
                .project("enterprise", "com.acme", "17", Optional.empty())
                .repositorySettings(Map.of(
                        "central", RepositorySettings.unauthenticated("central", ProjectConfig.MAVEN_CENTRAL),
                        "company", new RepositorySettings(
                                "company",
                                "https://repo.acme.example/maven",
                                Optional.of("company-artifactory"))))
                .repositoryCredentials(Map.of(
                        "company-artifactory",
                        new RepositoryCredentialSettings(
                                "company-artifactory",
                                "ARTIFACTORY_USERNAME",
                                "ARTIFACTORY_ACCESS_TOKEN")))
                .build();

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"company\" = { url = \"https://repo.acme.example/maven\", credentials = \"company-artifactory\" }"));
        assertTrue(toml.contains("[repositoryCredentials.\"company-artifactory\"]"));
        assertFalse(toml.contains("ReadPermanent"));
        assertEquals(
                "company-artifactory",
                parsed.repositorySettings().get("company").credentials().orElseThrow());
        assertEquals(
                "ARTIFACTORY_ACCESS_TOKEN",
                parsed.repositoryCredentials().get("company-artifactory").passwordEnv());
    }

    @Test
    void writesDependencyPolicyAndConstraints() {
        ProjectConfig config = writer.defaultApplicationConfig("enterprise", "com.acme", "com.acme.Main")
                .withDependencyPolicy(new DependencyPolicySettings(
                        List.of(new DependencyPolicyExclusion(
                                "commons-logging",
                                "commons-logging",
                                Optional.of("Use jcl-over-slf4j"))),
                        Map.of(
                                "org.apache.tomcat.embed:tomcat-embed-core",
                                new DependencyConstraint(
                                        "org.apache.tomcat.embed:tomcat-embed-core",
                                        "10.1.40",
                                        DependencyConstraintKind.STRICT,
                                        Optional.of("Container baseline")))));

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[dependencyPolicy]"));
        assertTrue(toml.contains("exclude = [{ group = \"commons-logging\", artifact = \"commons-logging\", reason = \"Use jcl-over-slf4j\" }]"));
        assertTrue(toml.contains("[dependencyConstraints]"));
        assertTrue(toml.contains("\"org.apache.tomcat.embed:tomcat-embed-core\" = { version = \"10.1.40\", kind = \"strict\", reason = \"Container baseline\" }"));
        assertEquals(config.dependencyPolicy(), parsed.dependencyPolicy());
    }

    @Test
    void writesDependencyConstraintVersionRefsWhenPresent() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [versions]
                tomcat = "10.1.40"

                [dependencyConstraints]
                "org.apache.tomcat.embed:tomcat-embed-core" = { versionRef = "tomcat", kind = "strict", reason = "Container baseline" }
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"org.apache.tomcat.embed:tomcat-embed-core\" = { versionRef = \"tomcat\", kind = \"strict\", reason = \"Container baseline\" }"));
        DependencyConstraint constraint = parsed.dependencyPolicy()
                .constraints()
                .get("org.apache.tomcat.embed:tomcat-embed-core");
        assertEquals("10.1.40", constraint.version());
        assertEquals("tomcat", constraint.versionRef().orElseThrow());
    }

    @Test
    void writesApiDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:managed-contract" = {}
                "com.acme:shared-contract" = { workspace = "modules/shared-contract" }
                "com.fasterxml.jackson.core:jackson-annotations" = "2.20.0"
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[api.dependencies]\n"));
        assertTrue(toml.contains("\"com.acme:managed-contract\" = {}"));
        assertTrue(toml.contains("\"com.acme:shared-contract\" = { workspace = \"modules/shared-contract\" }"));
        assertTrue(toml.contains("\"com.fasterxml.jackson.core:jackson-annotations\" = \"2.20.0\""));
        assertEquals(config.apiDependencies(), parsed.apiDependencies());
        assertEquals(config.managedApiDependencies(), parsed.managedApiDependencies());
        assertEquals(config.workspaceApiDependencies(), parsed.workspaceApiDependencies());
    }

    @Test
    void preservesApiDependenciesWhenEditingOtherSections() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");
        config = writer.addPlatform(config, "com.acme:enterprise-platform", "2026.1.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("1.0.0", parsed.apiDependencies().get("com.acme:contract"));
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
        assertEquals("2026.1.0", parsed.platforms().get("com.acme:enterprise-platform"));
    }

    @Test
    void writesRuntimeAndProvidedDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("web", "com.acme", "com.acme.Main");
        config = writer.addManagedDependency(config, DependencySection.RUNTIME, "com.h2database:h2");
        config = writer.addDependency(
                config,
                DependencySection.PROVIDED,
                "jakarta.servlet:jakarta.servlet-api",
                "6.1.0");
        config = writer.addPlatform(config, "org.springframework.boot:spring-boot-dependencies", "4.0.6");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[runtime.dependencies]\n\"com.h2database:h2\" = {}"));
        assertTrue(toml.contains("[provided.dependencies]\n\"jakarta.servlet:jakarta.servlet-api\" = \"6.1.0\""));
        assertTrue(parsed.managedRuntimeDependencies().contains("com.h2database:h2"));
        assertEquals("6.1.0", parsed.providedDependencies().get("jakarta.servlet:jakarta.servlet-api"));
        assertEquals("4.0.6", parsed.platforms().get("org.springframework.boot:spring-boot-dependencies"));
    }

    @Test
    void editingRuntimeDependencyRemovesConflictingMainScopeDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.h2database:h2" = "2.4.240"
                """);

        config = writer.addManagedDependency(config, DependencySection.RUNTIME, "com.h2database:h2");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertTrue(parsed.managedRuntimeDependencies().contains("com.h2database:h2"));
    }

    @Test
    void removingProvidedDependencyPreservesRuntimeDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [runtime.dependencies]
                "com.h2database:h2" = "2.4.240"

                [provided.dependencies]
                "jakarta.servlet:jakarta.servlet-api" = "6.1.0"
                """);

        config = writer.removeDependency(config, DependencySection.PROVIDED, "jakarta.servlet:jakarta.servlet-api");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("2.4.240", parsed.runtimeDependencies().get("com.h2database:h2"));
        assertTrue(parsed.providedDependencies().isEmpty());
    }

    @Test
    void writesDevDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("web", "com.acme", "com.acme.Main");
        config = writer.addManagedDependency(config, DependencySection.DEV, "org.springframework.boot:spring-boot-devtools");
        config = writer.addDependency(config, DependencySection.DEV, "com.acme:local-tool", "1.0.0");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[dev.dependencies]\n\"com.acme:local-tool\" = \"1.0.0\""));
        assertTrue(toml.contains("\"org.springframework.boot:spring-boot-devtools\" = {}"));
        assertEquals("1.0.0", parsed.devDependencies().get("com.acme:local-tool"));
        assertTrue(parsed.managedDevDependencies().contains("org.springframework.boot:spring-boot-devtools"));
    }

    @Test
    void editingDevDependencyRemovesConflictingRuntimeDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [runtime.dependencies]
                "com.acme:local-tool" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.DEV, "com.acme:local-tool", "2.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.runtimeDependencies().isEmpty());
        assertEquals("2.0.0", parsed.devDependencies().get("com.acme:local-tool"));
    }

    @Test
    void editingMainDependencyRemovesConflictingApiDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.MAIN, "com.acme:contract", "2.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.apiDependencies().isEmpty());
        assertEquals("2.0.0", parsed.dependencies().get("com.acme:contract"));
    }

    @Test
    void editingApiDependencyRemovesConflictingMainDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.API, "com.acme:contract", "2.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertEquals("2.0.0", parsed.apiDependencies().get("com.acme:contract"));
    }

    @Test
    void removingMainDependencyPreservesApiDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:contract" = "1.0.0"
                """);

        config = writer.removeDependency(config, DependencySection.MAIN, "com.acme:contract");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertEquals("1.0.0", parsed.apiDependencies().get("com.acme:contract"));
    }

    @Test
    void editsApiDependenciesAcrossVersionedManagedAndWorkspaceForms() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:managed-contract" = "1.0.0"
                "com.acme:shared-contract" = { workspace = "modules/shared-contract" }
                """);

        config = writer.addManagedDependency(config, DependencySection.API, "com.acme:managed-contract");
        config = writer.addDependency(config, DependencySection.API, "com.acme:shared-contract", "2.0.0");
        config = writer.removeDependency(config, DependencySection.API, "com.acme:missing-contract");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.managedApiDependencies().contains("com.acme:managed-contract"));
        assertFalse(parsed.apiDependencies().containsKey("com.acme:managed-contract"));
        assertEquals("2.0.0", parsed.apiDependencies().get("com.acme:shared-contract"));
        assertFalse(parsed.workspaceApiDependencies().containsKey("com.acme:shared-contract"));
    }

    @Test
    void writesCompilerSettingsWhenConfigured() {
        ProjectConfig config = configWithCompilerSettings();

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[compiler]\n"));
        assertTrue(toml.contains("generatedSources = \"build/generated/main\""));
        assertTrue(toml.contains("generatedTestSources = \"build/generated/test\""));
        assertTrue(toml.contains("release = \"17\""));
        assertTrue(toml.contains("encoding = \"UTF-8\""));
        assertTrue(toml.contains("args = [\"-Xlint:deprecation\", \"-parameters\"]"));
        assertTrue(toml.contains("testArgs = [\"-Xlint:unchecked\"]"));
        assertEquals(config.compilerSettings(), parsed.compilerSettings());
    }

    @Test
    void writesGeneratedSourceStepsWhenConfigured() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withGeneratedSources(
                        List.of(new GeneratedSourceStep(
                                "openapi",
                                GeneratedSourceKind.DECLARED_ROOT,
                                "java",
                                "target/generated/sources/openapi",
                                List.of("src/main/openapi/api.yaml"),
                                true,
                                false)),
                        List.of(new GeneratedSourceStep(
                                "fixtures",
                                GeneratedSourceKind.DECLARED_ROOT,
                                "java",
                                "target/generated/test-sources/fixtures",
                                List.of("src/test/fixtures/schema.json"),
                                false,
                                true))));

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[generated.main.openapi]\n"));
        assertTrue(toml.contains("kind = \"declared-root\""));
        assertTrue(toml.contains("output = \"target/generated/sources/openapi\""));
        assertTrue(toml.contains("inputs = [\"src/main/openapi/api.yaml\"]"));
        assertTrue(toml.contains("[generated.test.fixtures]\n"));
        assertTrue(toml.contains("required = false"));
        assertTrue(toml.contains("clean = true"));
        assertEquals(config.build().generatedMainSources(), parsed.build().generatedMainSources());
        assertEquals(config.build().generatedTestSources(), parsed.build().generatedTestSources());
    }

    @Test
    void writesOpenApiGeneratedSourceStepsWhenConfigured() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withGeneratedSources(
                        List.of(new GeneratedSourceStep(
                                "public-api",
                                GeneratedSourceKind.OPENAPI,
                                "java",
                                "target/generated/sources/openapi/public-api",
                                List.of("src/main/openapi/public-api.yaml"),
                                true,
                                true,
                                new OpenApiGenerationSettings(
                                        Optional.of("org.openapitools:openapi-generator-cli"),
                                        Optional.of("7.11.0"),
                                        Optional.empty(),
                                        Optional.of("spring"),
                                        Optional.of("spring-boot"),
                                        Optional.of("com.example.api"),
                                        Optional.of("com.example.api.model"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(false),
                                        Map.of("interfaceOnly", "true"),
                                        Map.of("useBeanValidation", "true"),
                                        Map.of("dateLibrary", "java8"),
                                        Map.of(),
                                        Map.of("OffsetDateTime", "Instant"),
                                        Map.of()))),
                        List.of()));

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[generated.openapiTool]\n"));
        assertTrue(toml.contains("coordinate = \"org.openapitools:openapi-generator-cli\""));
        assertTrue(toml.contains("version = \"7.11.0\""));
        assertTrue(toml.contains("[generated.main.public-api]\n"));
        assertTrue(toml.contains("kind = \"openapi\""));
        assertTrue(toml.contains("input = \"src/main/openapi/public-api.yaml\""));
        assertFalse(toml.contains("inputs = [\"src/main/openapi/public-api.yaml\"]"));
        assertTrue(toml.contains("validateSpec = false"));
        assertTrue(toml.contains("options = { \"interfaceOnly\" = \"true\" }"));
        assertTrue(toml.contains("additionalProperties = { \"useBeanValidation\" = \"true\" }"));
        assertTrue(toml.contains("configOptions = { \"dateLibrary\" = \"java8\" }"));
        assertEquals(config.build().generatedMainSources(), parsed.build().generatedMainSources());
    }

    @Test
    void writesOpenApiToolVersionRefWhenConfigured() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "openapi-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                openapi = "7.11.0"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                versionRef = "openapi"

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                generator = "spring"
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[generated.openapiTool]\n"));
        assertTrue(toml.contains("coordinate = \"org.openapitools:openapi-generator-cli\""));
        assertTrue(toml.contains("versionRef = \"openapi\""));
        assertFalse(toml.contains("version = \"7.11.0\""));
        assertEquals(config.versionAliases(), parsed.versionAliases());
        assertEquals(config.build().generatedMainSources(), parsed.build().generatedMainSources());
    }

    @Test
    void writesNativeSettingsWhenConfigured() {
        ProjectConfig original = configWithNativeSettings();

        ProjectConfig parsed = parser.parse(writer.write(original));

        assertEquals(original.nativeSettings(), parsed.nativeSettings());
    }

    @Test
    void writesPackageSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[package]\n"));
        assertTrue(toml.contains("mode = \"spring-boot\""));
        assertFalse(toml.contains("[package.metadata]"));
        assertEquals(original.packageSettings(), parsed.packageSettings());
    }

    @Test
    void writesSpringBootWarPackageSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT_WAR));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[package]\n"));
        assertTrue(toml.contains("mode = \"spring-boot-war\""));
        assertEquals(original.packageSettings(), parsed.packageSettings());
    }

    @Test
    void writesLibraryPackageSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", null)
                .withPackageSettings(new PackageSettings(
                        PackageMode.THIN,
                        true,
                        true,
                        true,
                        new PublicationMetadata(
                                "Hello Library",
                                "Demo library",
                                "https://example.com/hello",
                                "Apache-2.0",
                                List.of("Shawn"),
                                "https://example.com/hello.git",
                                "https://example.com/hello/issues"),
                        Map.of(
                                "Automatic-Module-Name", "com.example.hello",
                                "Bundle-SymbolicName", "com.example.hello")));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[package]\n"));
        assertTrue(toml.contains("sources = true"));
        assertTrue(toml.contains("javadoc = true"));
        assertTrue(toml.contains("tests = true"));
        assertTrue(toml.contains("[package.metadata]\n"));
        assertTrue(toml.contains("name = \"Hello Library\""));
        assertTrue(toml.contains("developers = [\"Shawn\"]"));
        assertTrue(toml.contains("[package.manifest]\n"));
        assertTrue(toml.contains("\"Automatic-Module-Name\" = \"com.example.hello\""));
        assertTrue(toml.contains("\"Bundle-SymbolicName\" = \"com.example.hello\""));
        assertEquals(original.packageSettings(), parsed.packageSettings());
    }

    @Test
    void writesQuarkusFrameworkSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[framework.quarkus]\n"));
        assertTrue(toml.contains("enabled = true"));
        assertTrue(toml.contains("package = \"fast-jar\""));
        assertEquals(original.frameworkSettings(), parsed.frameworkSettings());
    }

    @Test
    void writesBuildMetadataSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        new BuildMetadataSettings(true, true, true)));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[build.metadata]\n"));
        assertTrue(toml.contains("buildInfo = true"));
        assertTrue(toml.contains("git = true"));
        assertTrue(toml.contains("reproducible = true"));
        assertEquals(original.build().metadata(), parsed.build().metadata());
    }

    @Test
    void writesTestRuntimeSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withTestRuntime(new TestRuntimeSettings(
                        List.of("--add-opens=java.base/java.lang=ALL-UNNAMED"),
                        Map.of("logs.dir", "${project.root}/test-logs"),
                        Map.of("TZ", "America/Chicago", "APP_HOME", "${project.root}"),
                        List.of("failed", "skipped"))));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[test.runtime]\n"));
        assertTrue(toml.contains("jvmArgs = [\"--add-opens=java.base/java.lang=ALL-UNNAMED\"]"));
        assertTrue(toml.contains("systemProperties = { \"logs.dir\" = \"${project.root}/test-logs\" }"));
        assertTrue(toml.contains("environment = { \"APP_HOME\" = \"${project.root}\", \"TZ\" = \"America/Chicago\" }"));
        assertTrue(toml.contains("events = [\"failed\", \"skipped\"]"));
        assertEquals(original.build().testRuntime(), parsed.build().testRuntime());
    }

    @Test
    void preservesTestRuntimeSettingsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withTestRuntime(new TestRuntimeSettings(
                        List.of("-Dconfigured=true"),
                        Map.of("logs.dir", "${project.root}/test-logs"),
                        Map.of("TZ", "America/Chicago"),
                        List.of("failed"))));
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.build().testRuntime(), parsed.build().testRuntime());
    }

    @Test
    void writesResourceRootsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        List.of("src/main/resources", "target/generated/resources"),
                        List.of("src/test/resources", "target/generated/test-resources"),
                        BuildMetadataSettings.defaults()));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[resources]\n"));
        assertTrue(toml.contains("main = [\"src/main/resources\", \"target/generated/resources\"]"));
        assertTrue(toml.contains("test = [\"src/test/resources\", \"target/generated/test-resources\"]"));
        assertEquals(original.build().resourceRoots(), parsed.build().resourceRoots());
        assertEquals(original.build().testResourceRoots(), parsed.build().testResourceRoots());
    }

    @Test
    void writesResourceFilteringWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withResourceFiltering(resourceFilteringSettings()));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[resources.filtering]\n"));
        assertTrue(toml.contains("enabled = true"));
        assertTrue(toml.contains("test = true"));
        assertTrue(toml.contains("includes = [\"**/*.properties\", \"**/*.yml\"]"));
        assertTrue(toml.contains("missing = \"keep\""));
        assertTrue(toml.contains("[resources.tokens]\n"));
        assertTrue(toml.contains("\"literalName\" = { value = \"demo-app\" }"));
        assertTrue(toml.contains("\"projectVersion\" = { project = \"version\" }"));
        assertTrue(toml.contains("\"ciBuild\" = { env = \"CI_BUILD_NUMBER\" }"));
        assertEquals(original.build().resourceFiltering(), parsed.build().resourceFiltering());
    }

    @Test
    void preservesCompilerSettingsWhenEditingDependencies() {
        ProjectConfig config = configWithCompilerSettings();
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.compilerSettings(), parsed.compilerSettings());
    }

    @Test
    void preservesPackageSettingsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withPackageSettings(new PackageSettings(PackageMode.UBER));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.packageSettings(), parsed.packageSettings());
    }

    @Test
    void preservesFrameworkSettingsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
        config = config.withBuildSettings(BuildSettings.defaults());
        config = writer.addDependency(config, DependencySection.MAIN, "io.quarkus:quarkus-rest", "3.28.2");
        config = writer.addManagedDependency(config, DependencySection.TEST, "io.quarkus:quarkus-junit5");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.frameworkSettings(), parsed.frameworkSettings());
    }

    @Test
    void preservesBuildMetadataSettingsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        new BuildMetadataSettings(true, false, true)));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.build().metadata(), parsed.build().metadata());
    }

    @Test
    void preservesResourceRootsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        List.of("src/main/resources", "target/generated/resources"),
                        List.of("src/test/resources"),
                        BuildMetadataSettings.defaults()));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.build().resourceRoots(), parsed.build().resourceRoots());
        assertEquals(config.build().testResourceRoots(), parsed.build().testResourceRoots());
    }

    @Test
    void preservesResourceFilteringWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withResourceFiltering(resourceFilteringSettings()));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.build().resourceFiltering(), parsed.build().resourceFiltering());
    }

    @Test
    void addsDependenciesToCorrectSections() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("33.4.0-jre", parsed.dependencies().get("com.google.guava:guava"));
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
    }

    @Test
    void writesDependencyMetadataWhenConfigured() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withDependencyMetadata(Map.of(
                        DependencyMetadata.key("dependencies", "com.example:core"),
                        new DependencyMetadata(
                                "dependencies",
                                "com.example:core",
                                "1.0.0",
                                false,
                                null,
                                true,
                                false,
                                List.of(new DependencyExclusionSpec("com.example", "legacy-logging"))),
                        DependencyMetadata.key("dependencies", "com.example:publish-helper"),
                        new DependencyMetadata(
                                "dependencies",
                                "com.example:publish-helper",
                                "2.0.0",
                                false,
                                null,
                                false,
                                true,
                                List.of())));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:core", "1.0.0");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"com.example:core\" = { version = \"1.0.0\", optional = true, exclusions = [{ group = \"com.example\", artifact = \"legacy-logging\" }] }"));
        assertTrue(toml.contains("\"com.example:publish-helper\" = { version = \"2.0.0\", publishOnly = true }"));
        assertEquals(config.dependencyMetadata(), parsed.dependencyMetadata());
        assertEquals("1.0.0", parsed.dependencies().get("com.example:core"));
        assertFalse(parsed.dependencies().containsKey("com.example:publish-helper"));
    }

    @Test
    void removesNonPublishDependencyMetadataWhenRemovingDependency() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withDependencyMetadata(Map.of(
                        DependencyMetadata.key("dependencies", "com.example:core"),
                        new DependencyMetadata(
                                "dependencies",
                                "com.example:core",
                                "1.0.0",
                                false,
                                null,
                                true,
                                false,
                                List.of())));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:core", "1.0.0");
        config = writer.removeDependency(config, DependencySection.MAIN, "com.example:core");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertFalse(parsed.dependencies().containsKey("com.example:core"));
        assertFalse(parsed.dependencyMetadata().containsKey(DependencyMetadata.key("dependencies", "com.example:core")));
    }

    @Test
    void preservesNativeSettingsWhenEditingDependencies() {
        ProjectConfig config = configWithNativeSettings();
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");
        config = writer.removeDependency(config, DependencySection.MAIN, "com.google.guava:guava");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.nativeSettings(), parsed.nativeSettings());
    }

    @Test
    void preservesExplicitJavaTestSourceRootsWhenEditingDependencies() {
        ProjectConfig config = config()
                .build(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java", "src/integration-test/java")))
                .build();
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[test.sources]\njava = [\"src/test/java\", \"src/integration-test/java\"]"));
        assertEquals(config.build().testSources(), parsed.build().testSources());
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
    }

    @Test
    void preservesExplicitGroovyTestSourceRootsWhenEditingDependencies() {
        ProjectConfig config = config()
                .build(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        List.of("src/test/groovy")))
                .build();
        config = writer.addDependency(config, DependencySection.TEST, "org.spockframework:spock-core", "2.4-M5-groovy-4.0");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[test.sources]\ngroovy = [\"src/test/groovy\"]"));
        assertEquals(config.build().groovyTestSources(), parsed.build().groovyTestSources());
        assertEquals("2.4-M5-groovy-4.0", parsed.testDependencies().get("org.spockframework:spock-core"));
    }

    @Test
    void removesDependenciesFromCorrectSections() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");
        config = writer.removeDependency(config, DependencySection.MAIN, "com.google.guava:guava");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
    }

    @Test
    void formatsDependenciesDeterministically() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addDependency(config, DependencySection.MAIN, "org.slf4j:slf4j-api", "2.0.16");
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");

        String toml = writer.write(config);

        assertTrue(toml.indexOf("\"com.google.guava:guava\"") < toml.indexOf("\"org.slf4j:slf4j-api\""));
    }

    @Test
    void writesManagedDependencies() {
        ProjectConfig config = config()
                .project("spring", "com.example", "21", Optional.empty())
                .platforms(Map.of("org.springframework.boot:spring-boot-dependencies", "4.0.6"))
                .managedDependencies(Set.of("org.springframework.boot:spring-boot-starter-webmvc"))
                .managedTestDependencies(Set.of("org.junit.jupiter:junit-jupiter"))
                .build();

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"org.springframework.boot:spring-boot-starter-webmvc\" = {}"));
        assertEquals(config.platforms(), parsed.platforms());
        assertEquals(config.managedDependencies(), parsed.managedDependencies());
        assertEquals(config.managedTestDependencies(), parsed.managedTestDependencies());
    }

    @Test
    void writesWorkspaceDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }

                [test.dependencies]
                "com.acme:test-fixtures" = { workspace = "modules/test-fixtures" }
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("\"com.acme:core\" = { workspace = \"modules/core\" }"));
        assertTrue(toml.contains("\"com.acme:test-fixtures\" = { workspace = \"modules/test-fixtures\" }"));
        assertEquals(config.workspaceDependencies(), parsed.workspaceDependencies());
        assertEquals(config.workspaceTestDependencies(), parsed.workspaceTestDependencies());
    }

    @Test
    void editingDependenciesRemovesConflictingWorkspaceDependency() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);

        config = writer.addDependency(config, DependencySection.MAIN, "com.acme:core", "1.0.0");
        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("1.0.0", parsed.dependencies().get("com.acme:core"));
        assertTrue(parsed.workspaceDependencies().isEmpty());
    }

    @Test
    void writesAnnotationProcessorDeclarationsDeterministically() {
        ProjectConfig config = config()
                .project("micronaut", "com.example", "21", Optional.empty())
                .platforms(Map.of("io.micronaut.platform:micronaut-platform", "5.0.0"))
                .annotationProcessors(Map.of("org.mapstruct:mapstruct-processor", "1.6.3"))
                .managedAnnotationProcessors(Set.of("io.micronaut:micronaut-inject-java"))
                .testAnnotationProcessors(Map.of("com.example:test-processor", "1.0.0"))
                .managedTestAnnotationProcessors(Set.of("io.micronaut:micronaut-inject-java"))
                .build();

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[annotationProcessors]\n"));
        assertTrue(toml.contains("\"io.micronaut:micronaut-inject-java\" = {}"));
        assertTrue(toml.contains("\"org.mapstruct:mapstruct-processor\" = \"1.6.3\""));
        assertTrue(toml.indexOf("\"io.micronaut:micronaut-inject-java\" = {}")
                < toml.indexOf("\"org.mapstruct:mapstruct-processor\" = \"1.6.3\""));
        assertTrue(toml.contains("[test.annotationProcessors]\n"));
        assertEquals(config.annotationProcessors(), parsed.annotationProcessors());
        assertEquals(config.managedAnnotationProcessors(), parsed.managedAnnotationProcessors());
        assertEquals(config.testAnnotationProcessors(), parsed.testAnnotationProcessors());
        assertEquals(config.managedTestAnnotationProcessors(), parsed.managedTestAnnotationProcessors());
    }

    @Test
    void preservesAnnotationProcessorsWhenEditingDependencies() {
        ProjectConfig config = config()
                .project("micronaut", "com.example", "21", Optional.empty())
                .annotationProcessors(Map.of("org.mapstruct:mapstruct-processor", "1.6.3"))
                .managedAnnotationProcessors(Set.of("io.micronaut:micronaut-inject-java"))
                .testAnnotationProcessors(Map.of("com.example:test-processor", "1.0.0"))
                .build();

        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");
        config = writer.addPlatform(config, "io.micronaut.platform:micronaut-platform", "5.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.annotationProcessors(), parsed.annotationProcessors());
        assertEquals(config.managedAnnotationProcessors(), parsed.managedAnnotationProcessors());
        assertEquals(config.testAnnotationProcessors(), parsed.testAnnotationProcessors());
        assertEquals(config.managedTestAnnotationProcessors(), parsed.managedTestAnnotationProcessors());
    }

    @Test
    void addsManagedDependenciesToCorrectSections() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addManagedDependency(config, DependencySection.MAIN, "com.example:app");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-tool");
        config = writer.addManagedDependency(config, DependencySection.PROCESSOR, "com.example:processor");
        config = writer.addManagedDependency(config, DependencySection.TEST_PROCESSOR, "com.example:test-processor");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.managedDependencies().contains("com.example:app"));
        assertTrue(parsed.managedTestDependencies().contains("com.example:test-tool"));
        assertTrue(parsed.managedAnnotationProcessors().contains("com.example:processor"));
        assertTrue(parsed.managedTestAnnotationProcessors().contains("com.example:test-processor"));
    }

    @Test
    void editsAnnotationProcessorSectionsWithoutTouchingRuntimeDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "processor-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.example:processor-api" = "1.0.0"

                [annotationProcessors]
                "com.example:processor" = {}

                [test.annotationProcessors]
                "com.example:test-processor" = "1.0.0"
                """);

        config = writer.addDependency(config, DependencySection.PROCESSOR, "com.example:processor", "2.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST_PROCESSOR, "com.example:test-processor");
        config = writer.removeDependency(config, DependencySection.PROCESSOR, "com.example:missing-processor");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("1.0.0", parsed.dependencies().get("com.example:processor-api"));
        assertEquals("2.0.0", parsed.annotationProcessors().get("com.example:processor"));
        assertTrue(parsed.managedAnnotationProcessors().isEmpty());
        assertTrue(parsed.testAnnotationProcessors().isEmpty());
        assertTrue(parsed.managedTestAnnotationProcessors().contains("com.example:test-processor"));
    }

    @Test
    void explicitDependencyReplacesManagedDependency() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addManagedDependency(config, DependencySection.MAIN, "com.example:app");
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("1.0.0", parsed.dependencies().get("com.example:app"));
        assertTrue(parsed.managedDependencies().isEmpty());
    }

    @Test
    void addsAndRemovesPlatforms() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addPlatform(config, "com.example:enterprise-platform", "2026.1.0");
        config = writer.removePlatform(config, "com.example:enterprise-platform");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.platforms().isEmpty());
    }

    private static ProjectConfig configWithNativeSettings() {
        return config()
                .nativeSettings(new NativeSettings("hello-native", "target/native-custom", List.of("--no-fallback")))
                .build();
    }

    private static ResourceFilteringSettings resourceFilteringSettings() {
        return new ResourceFilteringSettings(
                true,
                true,
                List.of("**/*.properties", "**/*.yml"),
                ResourceMissingTokenPolicy.KEEP,
                Map.of(
                        "projectVersion", ResourceTokenSettings.project("version"),
                        "literalName", ResourceTokenSettings.literal("demo-app"),
                        "ciBuild", ResourceTokenSettings.env("CI_BUILD_NUMBER")));
    }

    private static ProjectConfig configWithCompilerSettings() {
        return config()
                .compilerSettings(new CompilerSettings(
                        "build/generated/main",
                        "build/generated/test",
                        "17",
                        "UTF-8",
                        List.of("-Xlint:deprecation", "-parameters"),
                        List.of("-Xlint:unchecked")))
                .build();
    }
}
