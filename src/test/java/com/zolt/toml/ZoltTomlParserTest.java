package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.project.QuarkusPackageMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ZoltTomlParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesValidExampleConfig() {
        ProjectConfig config = parser.parse(Path.of("examples/hello-zolt/zolt.toml"));

        assertEquals("hello-zolt", config.project().name());
        assertEquals("0.1.0", config.project().version());
        assertEquals("com.example", config.project().group());
        assertEquals("21", config.project().java());
        assertEquals("com.example.Main", config.project().main().orElseThrow());
        assertEquals("https://repo.maven.apache.org/maven2", config.repositories().get("central"));
        assertTrue(config.platforms().isEmpty());
        assertTrue(config.apiDependencies().isEmpty());
        assertTrue(config.managedApiDependencies().isEmpty());
        assertTrue(config.workspaceApiDependencies().isEmpty());
        assertEquals("33.4.0-jre", config.dependencies().get("com.google.guava:guava"));
        assertTrue(config.testDependencies().isEmpty());
        assertTrue(config.managedDependencies().isEmpty());
        assertTrue(config.annotationProcessors().isEmpty());
        assertTrue(config.managedAnnotationProcessors().isEmpty());
        assertTrue(config.testAnnotationProcessors().isEmpty());
        assertTrue(config.managedTestAnnotationProcessors().isEmpty());
        assertEquals("src/main/java", config.build().source());
        assertEquals(List.of("src/test/java"), config.build().testSources());
        assertEquals("target/test-classes", config.build().testOutput());
        assertEquals("target/generated/sources/annotations", config.compilerSettings().generatedSources());
        assertEquals("target/generated/test-sources/annotations", config.compilerSettings().generatedTestSources());
        assertEquals(PackageMode.THIN, config.packageSettings().mode());
    }

    @Test
    void parsesTestDependencies() {
        ProjectConfig config = parser.parse(Path.of("examples/junit-basic/zolt.toml"));

        assertEquals("1.11.4", config.testDependencies().get("org.junit.platform:junit-platform-console-standalone"));
    }

    @Test
    void preservesDependencyDeclarationOrder() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "ordered"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.example:alpha" = "1.0.0"
                "com.example:beta" = "1.0.0"
                "com.example:core" = { workspace = "modules/core" }
                "com.example:util" = { workspace = "modules/util" }

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = "5.11.4"
                "org.assertj:assertj-core" = "3.27.3"
                """);

        assertEquals(List.of("com.example:alpha", "com.example:beta"), new ArrayList<>(config.dependencies().keySet()));
        assertEquals(List.of("com.example:core", "com.example:util"), new ArrayList<>(config.workspaceDependencies().keySet()));
        assertEquals(List.of("org.junit.jupiter:junit-jupiter", "org.assertj:assertj-core"), new ArrayList<>(config.testDependencies().keySet()));
    }

    @Test
    void appliesRepositoryAndBuildDefaultsWhenOptionalSectionsAreMissing() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "tiny"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        assertEquals(ProjectConfig.MAVEN_CENTRAL, config.repositories().get("central"));
        assertTrue(config.platforms().isEmpty());
        assertTrue(config.apiDependencies().isEmpty());
        assertTrue(config.managedApiDependencies().isEmpty());
        assertTrue(config.workspaceApiDependencies().isEmpty());
        assertTrue(config.dependencies().isEmpty());
        assertTrue(config.managedDependencies().isEmpty());
        assertTrue(config.annotationProcessors().isEmpty());
        assertTrue(config.managedAnnotationProcessors().isEmpty());
        assertTrue(config.testAnnotationProcessors().isEmpty());
        assertTrue(config.managedTestAnnotationProcessors().isEmpty());
        assertFalse(config.project().main().isPresent());
        assertEquals("src/main/java", config.build().source());
        assertEquals("target/classes", config.build().output());
        assertEquals("target/generated/sources/annotations", config.compilerSettings().generatedSources());
        assertEquals("target/generated/test-sources/annotations", config.compilerSettings().generatedTestSources());
        assertEquals(PackageMode.THIN, config.packageSettings().mode());
        assertEquals("", config.nativeSettings().imageName());
        assertEquals("target/native", config.nativeSettings().output());
        assertTrue(config.nativeSettings().args().isEmpty());
    }

    @Test
    void parsesApiDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.fasterxml.jackson.core:jackson-annotations" = "2.20.0"
                "com.acme:managed-contract" = {}
                "com.acme:shared-contract" = { workspace = "modules/shared-contract" }
                """);

        assertEquals("2.20.0", config.apiDependencies().get("com.fasterxml.jackson.core:jackson-annotations"));
        assertTrue(config.managedApiDependencies().contains("com.acme:managed-contract"));
        assertEquals("modules/shared-contract", config.workspaceApiDependencies().get("com.acme:shared-contract"));
        assertTrue(config.dependencies().isEmpty());
        assertTrue(config.workspaceDependencies().isEmpty());
    }

    @Test
    void parsesRuntimeAndProvidedDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [runtime.dependencies]
                "com.h2database:h2" = {}
                "org.slf4j:slf4j-simple" = "2.0.17"

                [provided.dependencies]
                "jakarta.servlet:jakarta.servlet-api" = "6.1.0"
                "com.acme:managed-container-api" = {}

                [dev.dependencies]
                "org.springframework.boot:spring-boot-devtools" = {}
                "com.acme:local-tool" = "1.0.0"
                """);

        assertEquals("2.0.17", config.runtimeDependencies().get("org.slf4j:slf4j-simple"));
        assertTrue(config.managedRuntimeDependencies().contains("com.h2database:h2"));
        assertEquals("6.1.0", config.providedDependencies().get("jakarta.servlet:jakarta.servlet-api"));
        assertTrue(config.managedProvidedDependencies().contains("com.acme:managed-container-api"));
        assertEquals("1.0.0", config.devDependencies().get("com.acme:local-tool"));
        assertTrue(config.managedDevDependencies().contains("org.springframework.boot:spring-boot-devtools"));
    }

    @Test
    void rejectsDuplicateApiAndImplementationDependencyCoordinate() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [api.dependencies]
                        "com.acme:contract" = "1.0.0"

                        [dependencies]
                        "com.acme:contract" = "1.0.0"
                        """));

        assertEquals(
                "Dependency com.acme:contract is declared in both [api.dependencies] and [dependencies]. Keep it in one section.",
                exception.getMessage());
    }

    @Test
    void rejectsDuplicateMainRuntimeAndProvidedDependencyCoordinate() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [runtime.dependencies]
                        "com.h2database:h2" = "2.4.240"

                        [provided.dependencies]
                        "com.h2database:h2" = "2.4.240"
                        """));

        assertEquals(
                "Dependency com.h2database:h2 is declared in both [runtime.dependencies] and [provided.dependencies]. Keep it in one section.",
                exception.getMessage());
    }

    @Test
    void rejectsDuplicateDevAndRuntimeDependencyCoordinate() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [runtime.dependencies]
                        "org.springframework.boot:spring-boot-devtools" = "4.0.6"

                        [dev.dependencies]
                        "org.springframework.boot:spring-boot-devtools" = "4.0.6"
                        """));

        assertEquals(
                "Dependency org.springframework.boot:spring-boot-devtools is declared in both [runtime.dependencies] and [dev.dependencies]. Keep it in one section.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownApiField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [api]
                        exports = ["com.acme:contract"]
                        """));

        assertEquals(
                "Unknown field [api].exports in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedApiDependencyDeclaration() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "web"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [api.dependencies]
                        "com.acme:contract" = 42
                        """));

        assertEquals(
                "Invalid value for [api.dependencies].com.acme:contract in zolt.toml. Use a non-empty string version, {} for a platform-managed version, or { workspace = \"path\" } for a workspace member.",
                exception.getMessage());
    }

    @Test
    void parsesPlatformsAndManagedDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "spring"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = "4.0.6"

                [dependencies]
                "org.springframework.boot:spring-boot-starter-webmvc" = {}
                "org.slf4j:slf4j-api" = { version = "2.0.17" }

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = {}
                """);

        assertEquals(
                "4.0.6",
                config.platforms().get("org.springframework.boot:spring-boot-dependencies"));
        assertTrue(config.managedDependencies().contains("org.springframework.boot:spring-boot-starter-webmvc"));
        assertEquals("2.0.17", config.dependencies().get("org.slf4j:slf4j-api"));
        assertTrue(config.managedTestDependencies().contains("org.junit.jupiter:junit-jupiter"));
    }

    @Test
    void parsesWorkspaceDependencies() {
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

        assertEquals("modules/core", config.workspaceDependencies().get("com.acme:core"));
        assertEquals("modules/test-fixtures", config.workspaceTestDependencies().get("com.acme:test-fixtures"));
        assertTrue(config.dependencies().isEmpty());
        assertTrue(config.testDependencies().isEmpty());
    }

    @Test
    void rejectsWorkspaceDependencyWithVersion() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "api"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [dependencies]
                        "com.acme:core" = { version = "1.0.0", workspace = "modules/core" }
                        """));

        assertEquals(
                "Invalid value for [dependencies].com.acme:core in zolt.toml. Use either version or workspace, not both.",
                exception.getMessage());
    }

    @Test
    void rejectsBlankWorkspaceDependencyPath() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "api"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"

                        [dependencies]
                        "com.acme:core" = { workspace = "" }
                        """));

        assertEquals(
                "Invalid value for [dependencies].com.acme:core.workspace in zolt.toml. Use a non-empty workspace member path.",
                exception.getMessage());
    }

    @Test
    void parsesAnnotationProcessorDeclarations() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "micronaut"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [platforms]
                "io.micronaut.platform:micronaut-platform" = "5.0.0"

                [annotationProcessors]
                "io.micronaut:micronaut-inject-java" = {}
                "org.mapstruct:mapstruct-processor" = { version = "1.6.3" }

                [test.annotationProcessors]
                "io.micronaut:micronaut-inject-java" = {}
                "com.example:test-processor" = "1.0.0"
                """);

        assertTrue(config.managedAnnotationProcessors().contains("io.micronaut:micronaut-inject-java"));
        assertEquals("1.6.3", config.annotationProcessors().get("org.mapstruct:mapstruct-processor"));
        assertTrue(config.managedTestAnnotationProcessors().contains("io.micronaut:micronaut-inject-java"));
        assertEquals("1.0.0", config.testAnnotationProcessors().get("com.example:test-processor"));
    }

    @Test
    void parsesCompilerGeneratedSourceDirectories() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "processor-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [compiler]
                generatedSources = "build/generated/main"
                generatedTestSources = "build/generated/test"
                """);

        assertEquals("build/generated/main", config.compilerSettings().generatedSources());
        assertEquals("build/generated/test", config.compilerSettings().generatedTestSources());
    }

    @Test
    void parsesPackageSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "boot"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [package]
                mode = "spring-boot"
                """);

        assertEquals(PackageMode.SPRING_BOOT, config.packageSettings().mode());
    }

    @Test
    void parsesQuarkusPackageMode() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "quarkus-app"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [package]
                mode = "quarkus"
                """);

        assertEquals(PackageMode.QUARKUS, config.packageSettings().mode());
    }

    @Test
    void parsesQuarkusFrameworkSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "quarkus-app"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [framework.quarkus]
                enabled = true
                package = "fast-jar"
                """);

        assertTrue(config.frameworkSettings().quarkus().enabled());
        assertEquals(QuarkusPackageMode.FAST_JAR, config.frameworkSettings().quarkus().packageMode());
    }

    @Test
    void defaultsQuarkusFrameworkSettingsWhenOmitted() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "plain-app"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        assertFalse(config.frameworkSettings().quarkus().enabled());
        assertEquals(QuarkusPackageMode.FAST_JAR, config.frameworkSettings().quarkus().packageMode());
    }

    @Test
    void parsesBuildMetadataSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "boot"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [build.metadata]
                buildInfo = true
                git = true
                reproducible = true
                """);

        assertTrue(config.build().metadata().buildInfo());
        assertTrue(config.build().metadata().git());
        assertTrue(config.build().metadata().reproducible());
    }

    @Test
    void rejectsMalformedBuildMetadataSetting() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [build.metadata]
                        buildInfo = "yes"
                        """));

        assertEquals(
                "Invalid value for [build.metadata].buildInfo in zolt.toml. Use true or false.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownPackageMode() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [package]
                        mode = "war"
                        """));

        assertEquals(
                "Unsupported package mode `war` in zolt.toml. Supported package modes are: thin, spring-boot, quarkus, uber.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownPackageField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [package]
                        classifier = "all"
                        """));

        assertEquals(
                "Unknown field [package].classifier in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownQuarkusPackageMode() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [framework.quarkus]
                        package = "legacy-jar"
                        """));

        assertEquals(
                "Unsupported Quarkus package mode `legacy-jar` in zolt.toml. Supported Quarkus package modes are: fast-jar.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownQuarkusFrameworkField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [framework.quarkus]
                        devMode = true
                        """));

        assertEquals(
                "Unknown field [framework.quarkus].devMode in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedAnnotationProcessorDeclaration() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [annotationProcessors]
                        "io.micronaut:micronaut-inject-java" = 42
                        """));

        assertEquals(
                "Invalid value for [annotationProcessors].io.micronaut:micronaut-inject-java in zolt.toml. Use a non-empty string version or {} for a platform-managed version.",
                exception.getMessage());
    }

    @Test
    void parsesNativeSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [native]
                imageName = "demo-native"
                output = "target/native-custom"
                args = ["--no-fallback", "--native-image-info"]
                """);

        assertEquals("demo-native", config.nativeSettings().imageName());
        assertEquals("target/native-custom", config.nativeSettings().output());
        assertEquals(
                List.of("--no-fallback", "--native-image-info"),
                config.nativeSettings().args());
    }

    @Test
    void parsesExplicitJavaTestSourceRoots() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.sources]
                java = ["src/test/java", "src/integration-test/java"]
                """);

        assertEquals(
                List.of("src/test/java", "src/integration-test/java"),
                config.build().testSources());
    }

    @Test
    void parsesExplicitResourceRoots() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [resources]
                main = ["src/main/resources", "target/generated/resources"]
                test = ["src/test/resources", "target/generated/test-resources"]
                """);

        assertEquals(
                List.of("src/main/resources", "target/generated/resources"),
                config.build().resourceRoots());
        assertEquals(
                List.of("src/test/resources", "target/generated/test-resources"),
                config.build().testResourceRoots());
    }

    @Test
    void rejectsMalformedResourceRoots() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [resources]
                        main = "src/main/resources"
                        """));

        assertEquals(
                "Invalid value for [resources].main in zolt.toml. Use an array of strings.",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedJavaTestSourceRoots() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [test.sources]
                        java = "src/test/java"
                        """));

        assertEquals(
                "Invalid value for [test.sources].java in zolt.toml. Use an array of strings.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownTestSourceLanguage() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [test.sources]
                        groovy = ["src/test/groovy"]
                        """));

        assertEquals(
                "Unknown field [test.sources].groovy in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void defaultsNativeImageNameWhenNativeSectionIsPresent() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [native]
                output = "target/native-custom"
                """);

        assertEquals("demo", config.nativeSettings().imageName());
        assertEquals("target/native-custom", config.nativeSettings().output());
        assertTrue(config.nativeSettings().args().isEmpty());
    }

    @Test
    void invalidTomlFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project
                        name = "broken"
                        """));

        assertTrue(exception.getMessage().contains("Could not parse zolt.toml."));
        assertTrue(exception.getMessage().contains("Fix the TOML syntax"));
    }

    @Test
    void missingRequiredProjectFieldIsActionable() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "missing-version"
                        group = "com.example"
                        java = "21"
                        """));

        assertEquals(
                "Missing required field [project].version in zolt.toml. Add a non-empty string value.",
                exception.getMessage());
    }

    @Test
    void unknownTopLevelSectionFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [plugins]
                        custom = "nope"
                        """));

        assertEquals(
                "Unknown top-level section [plugins] in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void unknownProjectFieldFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"
                        packaging = "jar"
                        """));

        assertEquals(
                "Unknown field [project].packaging in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void unknownNativeFieldFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [native]
                        script = "native-image.sh"
                        """));

        assertEquals(
                "Unknown field [native].script in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void dependencyValuesMustBeStrings() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [dependencies]
                        "com.google.guava:guava" = 33
                        """));

        assertEquals(
                "Invalid value for [dependencies].com.google.guava:guava in zolt.toml. Use a non-empty string version, {} for a platform-managed version, or { workspace = \"path\" } for a workspace member.",
                exception.getMessage());
    }

    @Test
    void dependencyInlineTablesRejectUnknownFields() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [dependencies]
                        "com.google.guava:guava" = { scope = "compile" }
                        """));

        assertEquals(
                "Unknown field [dependencies.com.google.guava:guava].scope in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void nativeArgsMustBeNonEmptyStrings() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [native]
                        args = ["--no-fallback", ""]
                        """));

        assertEquals(
                "Invalid value for [native].args[1] in zolt.toml. Use a non-empty string.",
                exception.getMessage());
    }

    @Test
    void nativeOutputMustBeAString() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [native]
                        output = 123
                        """));

        assertEquals(
                "Invalid value for [native].output in zolt.toml. Use a non-empty string value.",
                exception.getMessage());
    }
}
