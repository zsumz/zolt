package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.DependencyMetadata;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.ResourceMissingTokenPolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        assertEquals("", config.compilerSettings().release());
        assertEquals("", config.compilerSettings().encoding());
        assertTrue(config.compilerSettings().args().isEmpty());
        assertTrue(config.compilerSettings().testArgs().isEmpty());
        assertEquals(PackageMode.THIN, config.packageSettings().mode());
    }

    @Test
    void parsesTestDependencies() {
        ProjectConfig config = parser.parse(Path.of("examples/junit-basic/zolt.toml"));

        assertEquals("1.11.4", config.testDependencies().get("org.junit.platform:junit-platform-console-standalone"));
    }

    @Test
    void petclinicFixtureDeclaresH2AsRuntimeOnly() {
        ProjectConfig config = parser.parse(Path.of("examples/spring-boot-petclinic-lite/zolt.toml"));

        assertTrue(config.managedRuntimeDependencies().contains("com.h2database:h2"));
        assertFalse(config.dependencies().containsKey("com.h2database:h2"));
        assertFalse(config.managedDependencies().contains("com.h2database:h2"));
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
        assertEquals("", config.compilerSettings().release());
        assertEquals("", config.compilerSettings().encoding());
        assertTrue(config.compilerSettings().args().isEmpty());
        assertTrue(config.compilerSettings().testArgs().isEmpty());
        assertEquals(PackageMode.THIN, config.packageSettings().mode());
        assertEquals("", config.nativeSettings().imageName());
        assertEquals("target/native", config.nativeSettings().output());
        assertTrue(config.nativeSettings().args().isEmpty());
    }

    @Test
    void parsesCredentialedRepositories() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "17"

                [repositories]
                "company" = { url = "https://repo.acme.example/maven", credentials = "company-artifactory" }
                "central" = "https://repo.maven.apache.org/maven2"

                [repositoryCredentials.company-artifactory]
                usernameEnv = "ARTIFACTORY_USERNAME"
                passwordEnv = "ARTIFACTORY_ACCESS_TOKEN"
                """);

        assertEquals("https://repo.acme.example/maven", config.repositories().get("company"));
        assertEquals(
                "company-artifactory",
                config.repositorySettings().get("company").credentials().orElseThrow());
        assertEquals(
                "ARTIFACTORY_USERNAME",
                config.repositoryCredentials().get("company-artifactory").usernameEnv());
        assertEquals(
                "ARTIFACTORY_ACCESS_TOKEN",
                config.repositoryCredentials().get("company-artifactory").passwordEnv());
    }

    @Test
    void rejectsRepositoryCredentialReferenceWithoutDefinition() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "17"

                [repositories]
                "company" = { url = "https://repo.acme.example/maven", credentials = "missing" }
                """));

        assertTrue(exception.getMessage().contains("Repository `company` references credentials `missing`"));
        assertTrue(exception.getMessage().contains("[repositoryCredentials.missing] is not defined"));
    }

    @Test
    void parsesDependencyPolicyAndConstraints() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "17"

                [dependencyPolicy]
                exclude = [
                  { group = "commons-logging", artifact = "commons-logging", reason = "Use jcl-over-slf4j" },
                  { group = "log4j", artifact = "log4j" }
                ]

                [dependencyConstraints]
                "org.apache.tomcat.embed:tomcat-embed-core" = { version = "10.1.40", kind = "strict", reason = "Container baseline" }
                """);

        assertEquals(2, config.dependencyPolicy().exclusions().size());
        assertEquals("commons-logging", config.dependencyPolicy().exclusions().getFirst().group());
        assertEquals(
                "Use jcl-over-slf4j",
                config.dependencyPolicy().exclusions().getFirst().reason().orElseThrow());
        assertEquals(
                "10.1.40",
                config.dependencyPolicy()
                        .constraints()
                        .get("org.apache.tomcat.embed:tomcat-embed-core")
                        .version());
    }

    @Test
    void rejectsMalformedDependencyConstraintCoordinate() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "17"

                [dependencyConstraints]
                "org.example:bad:1.0.0" = { version = "2.0.0", kind = "strict" }
                """));

        assertTrue(exception.getMessage().contains("Invalid coordinate `org.example:bad:1.0.0`"));
        assertTrue(exception.getMessage().contains("Use `group:artifact`"));
    }

    @Test
    void rejectsUnsupportedDependencyConstraintKind() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "enterprise"
                version = "0.1.0"
                group = "com.acme"
                java = "17"

                [dependencyConstraints]
                "org.example:library" = { version = "2.0.0", kind = "prefer" }
                """));

        assertTrue(exception.getMessage().contains("Unsupported dependency constraint kind `prefer`"));
        assertTrue(exception.getMessage().contains("strict"));
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
                "Invalid value for [api.dependencies].com.acme:contract in zolt.toml. Use a non-empty string version, { versionRef = \"alias\" }, {} for a platform-managed version, or { workspace = \"path\" } for a workspace member. Inline tables may also include optional, publishOnly, and exclusions metadata.",
                exception.getMessage());
    }

    @Test
    void parsesDependencyMetadata() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "library"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.example:core" = { version = "1.0.0", optional = true, exclusions = [{ group = "com.example", artifact = "legacy-logging" }] }
                "com.example:publish-helper" = { version = "2.0.0", publishOnly = true }
                "com.example:managed-publish" = { publishOnly = true }
                """);

        DependencyMetadata core = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.example:core"));
        DependencyMetadata publish = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.example:publish-helper"));
        DependencyMetadata managedPublish = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.example:managed-publish"));
        assertEquals("1.0.0", config.dependencies().get("com.example:core"));
        assertFalse(config.dependencies().containsKey("com.example:publish-helper"));
        assertFalse(config.managedDependencies().contains("com.example:managed-publish"));
        assertTrue(core.optional());
        assertFalse(core.publishOnly());
        assertEquals("com.example:legacy-logging", core.exclusions().getFirst().coordinate());
        assertEquals("2.0.0", publish.version());
        assertTrue(publish.publishOnly());
        assertTrue(managedPublish.managed());
        assertTrue(managedPublish.publishOnly());
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
    void parsesVersionAliasesForVersionBearingFields() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "aliases"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                boot = "4.0.6"
                slf4j = "2.0.17"
                lombok = "1.18.38"
                tomcat = "10.1.40"
                junit = "5.11.4"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = { versionRef = "boot" }

                [dependencies]
                "org.slf4j:slf4j-api" = { versionRef = "slf4j" }

                [annotationProcessors]
                "org.projectlombok:lombok" = { versionRef = "lombok" }

                [test.annotationProcessors]
                "org.projectlombok:lombok" = { versionRef = "lombok" }

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = { versionRef = "junit" }

                [dependencyConstraints]
                "org.apache.tomcat.embed:tomcat-embed-core" = { versionRef = "tomcat", kind = "strict" }
                """);

        assertEquals("4.0.6", config.platforms().get("org.springframework.boot:spring-boot-dependencies"));
        assertEquals(
                "boot",
                config.dependencyMetadata()
                        .get(DependencyMetadata.key(
                                "platforms",
                                "org.springframework.boot:spring-boot-dependencies"))
                        .versionRef());
        assertEquals("4.0.6", config.versionAliases().get("boot"));
        assertEquals("2.0.17", config.dependencies().get("org.slf4j:slf4j-api"));
        assertEquals("1.18.38", config.annotationProcessors().get("org.projectlombok:lombok"));
        assertEquals("1.18.38", config.testAnnotationProcessors().get("org.projectlombok:lombok"));
        assertEquals("5.11.4", config.testDependencies().get("org.junit.jupiter:junit-jupiter"));
        assertEquals(
                "10.1.40",
                config.dependencyPolicy()
                        .constraints()
                        .get("org.apache.tomcat.embed:tomcat-embed-core")
                        .version());
    }

    @Test
    void rejectsUnknownVersionAliasReference() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "aliases"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [dependencies]
                        "com.google.guava:guava" = { versionRef = "guava" }
                        """));

        assertEquals(
                "Unknown versionRef `guava` in [dependencies.com.google.guava:guava]. Add [versions].guava or use an explicit version.",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedVersionAliasName() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "aliases"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [versions]
                        "spring boot" = "4.0.6"
                        """));

        assertEquals(
                "Invalid [versions] alias `spring boot`. Alias names may contain only letters, digits, dot, underscore, and hyphen.",
                exception.getMessage());
    }

    @Test
    void rejectsVersionAndVersionRefTogether() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "aliases"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [versions]
                        guava = "33.4.8-jre"

                        [dependencies]
                        "com.google.guava:guava" = { version = "33.4.8-jre", versionRef = "guava" }
                        """));

        assertEquals(
                "Invalid value for [dependencies.com.google.guava:guava] in zolt.toml. Use either version or versionRef, not both.",
                exception.getMessage());
    }

    @Test
    void rejectsVersionRefInNonVersionField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "aliases"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [package]
                        versionRef = "guava"
                        """));

        assertEquals(
                "Invalid value for [package].versionRef in zolt.toml. versionRef is only supported for dependency, platform, constraint, and tool artifact versions.",
                exception.getMessage());
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
                "Invalid value for [dependencies].com.acme:core in zolt.toml. Use version, versionRef, or workspace; do not combine them.",
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
                release = "17"
                encoding = "UTF-8"
                args = ["-Xlint:deprecation", "-parameters"]
                testArgs = ["-Xlint:unchecked"]
                """);

        assertEquals("build/generated/main", config.compilerSettings().generatedSources());
        assertEquals("build/generated/test", config.compilerSettings().generatedTestSources());
        assertEquals("17", config.compilerSettings().release());
        assertEquals("UTF-8", config.compilerSettings().encoding());
        assertEquals(List.of("-Xlint:deprecation", "-parameters"), config.compilerSettings().args());
        assertEquals(List.of("-Xlint:unchecked"), config.compilerSettings().testArgs());
    }

    @Test
    void parsesGeneratedSourceSteps() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "declared-root"
                language = "java"
                output = "target/generated/sources/openapi"
                inputs = ["src/main/openapi/api.yaml"]

                [generated.test.fixtures]
                kind = "declared-root"
                language = "java"
                output = "target/generated/test-sources/fixtures"
                inputs = ["src/test/fixtures/schema.json"]
                required = false
                clean = true
                """);

        assertEquals(1, config.build().generatedMainSources().size());
        assertEquals("openapi", config.build().generatedMainSources().getFirst().id());
        assertEquals(GeneratedSourceKind.DECLARED_ROOT, config.build().generatedMainSources().getFirst().kind());
        assertEquals("java", config.build().generatedMainSources().getFirst().language());
        assertEquals("target/generated/sources/openapi", config.build().generatedMainSources().getFirst().output());
        assertEquals(List.of("src/main/openapi/api.yaml"), config.build().generatedMainSources().getFirst().inputs());
        assertTrue(config.build().generatedMainSources().getFirst().required());
        assertFalse(config.build().generatedMainSources().getFirst().clean());
        assertEquals(1, config.build().generatedTestSources().size());
        assertEquals("fixtures", config.build().generatedTestSources().getFirst().id());
        assertFalse(config.build().generatedTestSources().getFirst().required());
        assertTrue(config.build().generatedTestSources().getFirst().clean());
    }

    @Test
    void parsesOpenApiGeneratedSourceStepsWithMergedPresetSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "openapi-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                version = "7.11.0"

                [generated.openapiPresets.spring-api]
                generator = "spring"
                library = "spring-boot"
                apiPackage = "com.example.api"
                modelPackage = "com.example.api.model"
                validateSpec = true
                options = { interfaceOnly = "true", useTags = "true" }
                additionalProperties = { generatedAnnotation = "false" }
                configOptions = { dateLibrary = "java8" }
                typeMappings = { OffsetDateTime = "Instant" }

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                preset = "spring-api"
                modelPackage = "com.example.public.model"
                validateSpec = false
                options = { hideGenerationTimestamp = "true", useTags = "false" }
                additionalProperties = { useBeanValidation = "true" }
                configOptions = { useSpringBoot3 = "true" }
                importMappings = { Instant = "java.time.Instant" }
                """);

        assertEquals(1, config.build().generatedMainSources().size());
        var step = config.build().generatedMainSources().getFirst();
        assertEquals("public-api", step.id());
        assertEquals(GeneratedSourceKind.OPENAPI, step.kind());
        assertEquals(List.of("src/main/openapi/public-api.yaml"), step.inputs());
        assertTrue(step.required());
        assertTrue(step.clean());
        assertEquals("org.openapitools:openapi-generator-cli", step.openApi().toolCoordinate().orElseThrow());
        assertEquals("7.11.0", step.openApi().toolVersion().orElseThrow());
        assertEquals("spring-api", step.openApi().preset().orElseThrow());
        assertEquals("spring", step.openApi().generator().orElseThrow());
        assertEquals("spring-boot", step.openApi().library().orElseThrow());
        assertEquals("com.example.api", step.openApi().apiPackage().orElseThrow());
        assertEquals("com.example.public.model", step.openApi().modelPackage().orElseThrow());
        assertFalse(step.openApi().validateSpec().orElseThrow());
        assertEquals(Map.of(
                "hideGenerationTimestamp", "true",
                "interfaceOnly", "true",
                "useTags", "false"), step.openApi().options());
        assertEquals(Map.of(
                "generatedAnnotation", "false",
                "useBeanValidation", "true"), step.openApi().additionalProperties());
        assertEquals(Map.of(
                "dateLibrary", "java8",
                "useSpringBoot3", "true"), step.openApi().configOptions());
        assertEquals(Map.of("OffsetDateTime", "Instant"), step.openApi().typeMappings());
        assertEquals(Map.of("Instant", "java.time.Instant"), step.openApi().importMappings());
    }

    @Test
    void parsesMultipleOpenApiGeneratedSourceStepsWithSharedPresetOverrides() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "openapi-multi-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                version = "7.11.0"

                [generated.openapiPresets.spring-api]
                generator = "spring"
                library = "spring-boot"
                apiPackage = "com.example.api"
                modelPackage = "com.example.api.model"
                configOptions = { useSpringBoot3 = "true" }

                [generated.main.integration-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/integration-api.yaml"
                output = "target/generated/sources/openapi/integration-api"
                preset = "spring-api"
                apiPackage = "com.example.integration.api"
                modelPackage = "com.example.integration.model"

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                preset = "spring-api"
                apiPackage = "com.example.public.api"
                modelPackage = "com.example.public.model"
                """);

        assertEquals(List.of("integration-api", "public-api"), config.build().generatedMainSources().stream()
                .map(com.zolt.project.GeneratedSourceStep::id)
                .toList());
        var integration = config.build().generatedMainSources().get(0);
        var publicApi = config.build().generatedMainSources().get(1);
        assertEquals("target/generated/sources/openapi/integration-api", integration.output());
        assertEquals("target/generated/sources/openapi/public-api", publicApi.output());
        assertEquals("com.example.integration.api", integration.openApi().apiPackage().orElseThrow());
        assertEquals("com.example.public.api", publicApi.openApi().apiPackage().orElseThrow());
        assertEquals("com.example.integration.model", integration.openApi().modelPackage().orElseThrow());
        assertEquals("com.example.public.model", publicApi.openApi().modelPackage().orElseThrow());
        assertEquals("spring", integration.openApi().generator().orElseThrow());
        assertEquals("spring", publicApi.openApi().generator().orElseThrow());
        assertEquals(Map.of("useSpringBoot3", "true"), integration.openApi().configOptions());
        assertEquals(Map.of("useSpringBoot3", "true"), publicApi.openApi().configOptions());
    }

    @Test
    void rejectsGeneratedSourceCommands() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "declared-root"
                language = "java"
                output = "target/generated/sources/openapi"
                inputs = ["src/main/openapi/api.yaml"]
                command = "generate"
                """));

        assertTrue(exception.getMessage().contains("Unknown field [generated.main.openapi].command"));
    }

    @Test
    void rejectsInvalidOpenApiOptionMapShapes() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/api.yaml"
                output = "target/generated/sources/openapi"
                configOptions = { useSpringBoot3 = true }
                """));

        assertTrue(exception.getMessage().contains("Invalid value for [generated.main.openapi.configOptions].useSpringBoot3"));
        assertTrue(exception.getMessage().contains("Use a non-empty string value."));
    }

    @Test
    void rejectsInvalidOpenApiValidateSpecValue() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "openapi-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/api.yaml"
                output = "target/generated/sources/openapi"
                validateSpec = "false"
                """));

        assertTrue(exception.getMessage().contains("Invalid value for [generated.main.openapi].validateSpec"));
        assertTrue(exception.getMessage().contains("Use true or false"));
    }

    @Test
    void rejectsOpenApiPostProcessingOptions() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/api.yaml"
                output = "target/generated/sources/openapi"
                options = { enablePostProcessFile = "true" }
                """));

        assertTrue(exception.getMessage().contains(
                "Unsupported OpenAPI generator option [generated.main.openapi.options].enablePostProcessFile"));
        assertTrue(exception.getMessage().contains("Zolt does not run generator post-processing hooks"));
    }

    @Test
    void rejectsOpenApiPresetPostProcessingOptions() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.openapiPresets.spring-api]
                additionalProperties = { postProcessFile = "format.sh" }

                [generated.main.openapi]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/api.yaml"
                output = "target/generated/sources/openapi"
                preset = "spring-api"
                """));

        assertTrue(exception.getMessage().contains(
                "Unsupported OpenAPI generator option [generated.openapiPresets.spring-api.additionalProperties].postProcessFile"));
        assertTrue(exception.getMessage().contains("model the behavior as a Zolt-owned generated-source feature"));
    }

    @Test
    void rejectsUnsupportedGeneratedSourceLanguages() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "declared-root"
                language = "kotlin"
                output = "target/generated/sources/openapi"
                inputs = ["src/main/openapi/api.yaml"]
                """));

        assertTrue(exception.getMessage().contains("Unsupported generated source language `kotlin`"));
    }

    @Test
    void rejectsGeneratedSourceStepsWithoutInputs() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "declared-root"
                language = "java"
                output = "target/generated/sources/openapi"
                inputs = []
                """));

        assertTrue(exception.getMessage().contains("Add at least one project-relative input path"));
    }

    @Test
    void rejectsCompilerOwnedJavacArgs() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "bad-compiler-args"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [compiler]
                args = ["--release", "17"]
                """));

        assertTrue(exception.getMessage().contains("Zolt owns `--release`"));
        assertTrue(exception.getMessage().contains("[compiler].release"));
    }

    @Test
    void parsesTestRuntimeSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "test-runtime"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.runtime]
                jvmArgs = ["--add-opens=java.base/java.lang=ALL-UNNAMED"]
                systemProperties = { "logs.dir" = "${project.root}/test-logs" }
                environment = { TZ = "America/Chicago", APP_HOME = "${project.root}" }
                events = ["failed", "skipped"]
                """);

        assertEquals(
                List.of("--add-opens=java.base/java.lang=ALL-UNNAMED"),
                config.build().testRuntime().jvmArgs());
        assertEquals(
                Map.of("logs.dir", "${project.root}/test-logs"),
                config.build().testRuntime().systemProperties());
        assertEquals(
                Map.of("APP_HOME", "${project.root}", "TZ", "America/Chicago"),
                config.build().testRuntime().environment());
        assertEquals(List.of("failed", "skipped"), config.build().testRuntime().events());
    }

    @Test
    void rejectsUnknownTestRuntimeEvents() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "bad-test-runtime"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.runtime]
                events = ["verbose"]
                """));

        assertTrue(exception.getMessage().contains("Unsupported test runtime event `verbose`"));
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
                sources = true
                javadoc = true
                tests = true

                [package.metadata]
                name = "Demo Library"
                description = "A demo library"
                url = "https://example.com/demo"
                license = "Apache-2.0"
                developers = ["Shawn"]
                scm = "https://example.com/demo.git"
                issues = "https://example.com/demo/issues"

                [package.manifest]
                "Automatic-Module-Name" = "com.example.boot"
                "Bundle-SymbolicName" = "com.example.boot"
                """);

        assertEquals(PackageMode.SPRING_BOOT, config.packageSettings().mode());
        assertTrue(config.packageSettings().sources());
        assertTrue(config.packageSettings().javadoc());
        assertTrue(config.packageSettings().tests());
        assertEquals("Demo Library", config.packageSettings().metadata().name());
        assertEquals("A demo library", config.packageSettings().metadata().description());
        assertEquals("https://example.com/demo", config.packageSettings().metadata().url());
        assertEquals("Apache-2.0", config.packageSettings().metadata().license());
        assertEquals(List.of("Shawn"), config.packageSettings().metadata().developers());
        assertEquals("https://example.com/demo.git", config.packageSettings().metadata().scm());
        assertEquals("https://example.com/demo/issues", config.packageSettings().metadata().issues());
        assertEquals(Map.of(
                "Automatic-Module-Name", "com.example.boot",
                "Bundle-SymbolicName", "com.example.boot"), config.packageSettings().manifestAttributes());
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
    void parsesWarPackageModes() {
        ProjectConfig war = parser.parse("""
                [project]
                name = "webapp"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [package]
                mode = "war"
                """);
        ProjectConfig springBootWar = parser.parse("""
                [project]
                name = "boot-webapp"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [package]
                mode = "spring-boot-war"
                """);

        assertEquals(PackageMode.WAR, war.packageSettings().mode());
        assertEquals(PackageMode.SPRING_BOOT_WAR, springBootWar.packageSettings().mode());
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
                        mode = "ear"
                        """));

        assertEquals(
                "Unsupported package mode `ear` in zolt.toml. Supported package modes are: thin, spring-boot, war, spring-boot-war, quarkus, uber.",
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
                "Invalid value for [annotationProcessors].io.micronaut:micronaut-inject-java in zolt.toml. Use a non-empty string version, { versionRef = \"alias\" }, or {} for a platform-managed version. Inline tables may also include optional, publishOnly, and exclusions metadata.",
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
    void parsesExplicitGroovyTestSourceRoots() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.sources]
                java = ["src/test/java"]
                groovy = ["src/test/groovy", "src/integration-test/groovy"]
                """);

        assertEquals(List.of("src/test/java"), config.build().testSources());
        assertEquals(
                List.of("src/test/groovy", "src/integration-test/groovy"),
                config.build().groovyTestSources());
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
    void parsesResourceFilteringSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [resources.filtering]
                enabled = true
                test = true
                includes = ["**/*.properties", "**/*.yml"]
                missing = "keep"

                [resources.tokens]
                projectVersion = { project = "version" }
                literalName = { value = "demo-app" }
                ciBuild = { env = "CI_BUILD_NUMBER" }
                """);

        assertTrue(config.build().resourceFiltering().enabled());
        assertTrue(config.build().resourceFiltering().testEnabled());
        assertEquals(List.of("**/*.properties", "**/*.yml"), config.build().resourceFiltering().includes());
        assertEquals(ResourceMissingTokenPolicy.KEEP, config.build().resourceFiltering().missing());
        assertEquals(
                "version",
                config.build().resourceFiltering().tokens().get("projectVersion").project().orElseThrow());
        assertEquals(
                "demo-app",
                config.build().resourceFiltering().tokens().get("literalName").value().orElseThrow());
        assertEquals(
                "CI_BUILD_NUMBER",
                config.build().resourceFiltering().tokens().get("ciBuild").env().orElseThrow());
    }

    @Test
    void rejectsResourceTokenWithMultipleSources() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [resources.tokens]
                        projectVersion = { project = "version", value = "0.1.0" }
                        """));

        assertEquals(
                "Invalid value for [resources.tokens].projectVersion in zolt.toml. Declare exactly one of value, env, or project.",
                exception.getMessage());
    }

    @Test
    void rejectsResourceTokenWithUnsupportedProjectField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [resources.tokens]
                        artifact = { project = "artifactId" }
                        """));

        assertEquals(
                "Invalid value for [resources.tokens].artifact.project in zolt.toml. Supported project fields are: name, version, group, java, main.",
                exception.getMessage());
    }

    @Test
    void rejectsUnsupportedResourceFilteringMissingPolicy() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [resources.filtering]
                        enabled = true
                        missing = "ignore"
                        """));

        assertTrue(exception.getMessage().contains("Unsupported resource filtering missing-token policy `ignore`"));
        assertTrue(exception.getMessage().contains("fail, keep"));
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
                        kotlin = ["src/test/kotlin"]
                        """));

        assertEquals(
                "Unknown field [test.sources].kotlin in zolt.toml. Remove it or check the spelling.",
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
                "Invalid value for [dependencies].com.google.guava:guava in zolt.toml. Use a non-empty string version, { versionRef = \"alias\" }, {} for a platform-managed version, or { workspace = \"path\" } for a workspace member. Inline tables may also include optional, publishOnly, and exclusions metadata.",
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
