package com.zolt.toml;

import static com.zolt.toml.ProjectConfigFixture.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import java.util.List;
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
    void representativeConfigsRoundTripThroughCanonicalToml() {
        for (RoundTripCase scenario : representativeRoundTripConfigs()) {
            ProjectConfig parsed = parser.parse(scenario.toml());
            ProjectConfig reparsed = parser.parse(writer.write(parsed));

            assertEquals(parsed, reparsed, scenario.name());
        }
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
    void preservesCompilerSettingsWhenEditingDependencies() {
        ProjectConfig config = configWithCompilerSettings();
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.compilerSettings(), parsed.compilerSettings());
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

    private static List<RoundTripCase> representativeRoundTripConfigs() {
        return List.of(
                new RoundTripCase(
                        "root project",
                        """
                        [project]
                        name = "root-app"
                        version = "1.0.0"
                        group = "com.example"
                        java = "21"
                        main = "com.example.Main"

                        [repositories]
                        "central" = "https://repo.maven.apache.org/maven2"

                        [dependencies]
                        "com.google.guava:guava" = "33.4.8-jre"
                        """),
                new RoundTripCase(
                        "workspace",
                        """
                        [project]
                        name = "workspace-api"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [api.dependencies]
                        "com.acme:shared-contract" = { workspace = "modules/shared-contract" }

                        [dependencies]
                        "com.acme:core" = { workspace = "modules/core" }

                        [test.dependencies]
                        "com.acme:test-fixtures" = { workspace = "modules/test-fixtures" }
                        """),
                new RoundTripCase(
                        "spring boot",
                        """
                        [project]
                        name = "spring-service"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"
                        main = "com.example.Application"

                        [versions]
                        boot = "4.0.6"

                        [platforms]
                        "org.springframework.boot:spring-boot-dependencies" = { versionRef = "boot" }

                        [dependencies]
                        "org.springframework.boot:spring-boot-starter-webmvc" = {}

                        [runtime.dependencies]
                        "com.h2database:h2" = {}

                        [dev.dependencies]
                        "org.springframework.boot:spring-boot-devtools" = {}

                        [package]
                        mode = "spring-boot-war"
                        """),
                new RoundTripCase(
                        "micronaut processors",
                        """
                        [project]
                        name = "micronaut-http"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [platforms]
                        "io.micronaut.platform:micronaut-platform" = "5.0.0"

                        [dependencies]
                        "io.micronaut:micronaut-http-server-netty" = {}

                        [annotationProcessors]
                        "io.micronaut:micronaut-inject-java" = {}
                        "org.mapstruct:mapstruct-processor" = "1.6.3"

                        [test.annotationProcessors]
                        "io.micronaut:micronaut-inject-java" = {}
                        """),
                new RoundTripCase(
                        "quarkus config",
                        """
                        [project]
                        name = "quarkus-http"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"
                        main = "com.example.quarkus.HelloResource"

                        [dependencies]
                        "io.quarkus:quarkus-rest" = "3.28.2"

                        [test.dependencies]
                        "io.quarkus:quarkus-junit5" = "3.28.2"

                        [package]
                        mode = "quarkus"

                        [framework.quarkus]
                        enabled = true
                        package = "fast-jar"
                        """),
                new RoundTripCase(
                        "generated sources",
                        """
                        [project]
                        name = "generated-demo"
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
                        library = "spring-boot"
                        configOptions = { useSpringBoot3 = "true" }

                        [generated.test.fixtures]
                        kind = "declared-root"
                        language = "java"
                        inputs = ["src/test/fixtures"]
                        output = "target/generated/test-sources/fixtures"
                        required = false
                        clean = true
                        """),
                new RoundTripCase(
                        "package metadata",
                        """
                        [project]
                        name = "library"
                        version = "1.0.0"
                        group = "com.example"
                        java = "21"

                        [package]
                        mode = "thin"
                        sources = true
                        javadoc = true
                        tests = true

                        [package.metadata]
                        name = "Example Library"
                        description = "A reusable Java library."
                        url = "https://example.com/library"
                        license = "Apache-2.0"
                        developers = ["Zolt maintainers"]
                        scm = "https://example.com/library.git"
                        issues = "https://example.com/library/issues"

                        [package.manifest]
                        "Automatic-Module-Name" = "com.example.library"
                        """),
                new RoundTripCase(
                        "publish-only metadata",
                        """
                        [project]
                        name = "publish-metadata"
                        version = "1.0.0"
                        group = "com.example"
                        java = "21"

                        [dependencies]
                        "com.example:api" = { version = "1.0.0", optional = true, exclusions = [{ group = "com.example", artifact = "legacy" }] }
                        "com.example:publish-helper" = { version = "2.0.0", publishOnly = true }
                        """));
    }

    private record RoundTripCase(String name, String toml) {
    }
}
