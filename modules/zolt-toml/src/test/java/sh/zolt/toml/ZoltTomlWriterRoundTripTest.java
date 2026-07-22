package sh.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.project.ProjectConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ZoltTomlWriterRoundTripTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void representativeConfigsRoundTripThroughCanonicalToml() {
        for (RoundTripCase scenario : representativeRoundTripConfigs()) {
            ProjectConfig parsed = parser.parse(scenario.toml());
            ProjectConfig reparsed = parser.parse(writer.write(parsed));

            assertEquals(parsed, reparsed, scenario.name());
        }
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
                        "central-ready package metadata",
                        """
                        [project]
                        name = "library"
                        version = "1.0.0"
                        group = "com.example"
                        java = "21"

                        [package.metadata]
                        name = "Example Library"
                        description = "A reusable Java library."
                        url = "https://example.com/library"
                        license = "Apache-2.0"
                        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        developers = ["Zolt maintainers"]
                        scm = "https://example.com/library.git"
                        scmConnection = "scm:git:https://example.com/library.git"
                        scmDeveloperConnection = "scm:git:ssh://git@example.com/library.git"
                        scmTag = "v1.0.0"
                        issues = "https://example.com/library/issues"

                        [package.metadata.developer.ada]
                        name = "Ada Lovelace"
                        email = "ada@example.com"
                        organization = "Analytical Engines"
                        url = "https://example.com/ada"
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
