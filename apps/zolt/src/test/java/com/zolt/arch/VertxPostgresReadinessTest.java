package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class VertxPostgresReadinessTest {
    private static final Path EXAMPLE = RepositoryPaths.root().resolve("examples/vertx-postgres-crud");

    @Test
    void postgresCrudExampleKeepsZoltOnlyProjectShape() throws IOException {
        Set<String> forbiddenBuildFiles = Set.of(
                "pom.xml",
                "build.gradle",
                "build.gradle.kts",
                "settings.gradle",
                "settings.gradle.kts",
                "gradlew",
                "mvnw");

        try (Stream<Path> paths = Files.walk(EXAMPLE)) {
            assertTrue(
                    paths.filter(Files::isRegularFile)
                            .map(path -> path.getFileName().toString())
                            .noneMatch(forbiddenBuildFiles::contains),
                    "Vert.x PostgreSQL readiness example must stay free of Maven and Gradle build files");
        }
        assertTrue(Files.isRegularFile(EXAMPLE.resolve("zolt.toml")));
    }

    @Test
    void postgresCrudExampleKeepsNativeImageContractInProjectConfig() throws IOException {
        String zoltToml = Files.readString(EXAMPLE.resolve("zolt.toml"));

        assertTrue(zoltToml.contains("main = \"com.example.vertx.postgres.VertxPostgresCrudApplication\""));
        assertTrue(zoltToml.contains("\"io.vertx:vertx-stack-depchain\" = \"4.5.11\""));
        assertTrue(zoltToml.contains("\"io.vertx:vertx-core\" = { exclusions = ["));
        assertTrue(zoltToml.contains("artifact = \"netty-tcnative-boringssl-static\""));
        assertTrue(zoltToml.contains("\"io.vertx:vertx-web\" = {}"));
        assertTrue(zoltToml.contains("\"io.vertx:vertx-pg-client\" = {}"));
        assertTrue(zoltToml.contains("\"org.slf4j:slf4j-simple\" = \"2.0.17\""));

        assertTrue(zoltToml.contains("[native]"));
        assertTrue(zoltToml.contains("imageName = \"vertx-postgres-crud\""));
        assertTrue(zoltToml.contains("output = \"target/native\""));
        assertTrue(zoltToml.contains("\"--no-fallback\""));
        assertTrue(zoltToml.contains("--initialize-at-run-time=io.netty.channel,io.netty.handler.ssl"));
        assertTrue(zoltToml.contains("--initialize-at-build-time=io.netty.buffer.UnpooledByteBufAllocator"));
        assertTrue(zoltToml.contains("org.slf4j"));
        assertFalse(
                zoltToml.contains("native-image-agent"),
                "Vert.x PostgreSQL readiness should not depend on tracing-agent generated configuration");
    }

    @Test
    void postgresCrudReadmeNamesReadinessGatesAndValidationScope() throws IOException {
        String readme = Files.readString(EXAMPLE.resolve("README.md"));

        assertTrue(readme.contains("Malformed create/update bodies, invalid JSON, and invalid note ids return `400` JSON errors"));
        assertTrue(readme.contains("Note ids must be positive integers across read, update, and delete routes; `0` and negative ids are rejected."));
        assertTrue(readme.contains("Database operation failures return `500` JSON errors."));
        assertTrue(readme.contains("JSON responses use `content-type: application/json`; successful deletes return `204` with no response body."));
        assertTrue(readme.contains("PGNOTES_TABLE"));
        assertTrue(readme.contains("Updating or deleting a missing note returns a `404` JSON error."));
        assertTrue(readme.contains("http://127.0.0.1:18092/notes/not-a-number"));
        assertTrue(readme.contains("ZOLT_VERTX_POSTGRES_SMOKE_ZOLT=scripts/bootstrap-zolt-jvm scripts/smoke-vertx-postgres-crud"));
        assertTrue(readme.contains("ZOLT_VERTX_POSTGRES_NATIVE_SMOKE_ZOLT=scripts/bootstrap-zolt-jvm scripts/smoke-vertx-postgres-native"));
        assertTrue(readme.contains("probe the CRUD API plus validation responses"));
        assertTrue(readme.contains("smoke probes also require JSON-bearing responses to declare `content-type: application/json`"));
        assertTrue(readme.contains("smoke script does not inject hidden native-image arguments"));
    }
}
