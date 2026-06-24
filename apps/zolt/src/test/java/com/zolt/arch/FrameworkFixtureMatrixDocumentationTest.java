package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FrameworkFixtureMatrixDocumentationTest {
    @Test
    void frameworkFixtureMatrixNamesExactFixturesAndSupportBoundaries() throws IOException {
        Path root = RepositoryPaths.root();
        String matrix = Files.readString(root.resolve("docs/framework-fixture-matrix.md"));

        assertTrue(matrix.contains("`scripts/smoke-spring-boot`"));
        assertTrue(matrix.contains("`scripts/smoke-spring-boot-enterprise-canary`"));
        assertTrue(matrix.contains("`scripts/smoke-spring-boot-native-aot-canary`"));
        assertTrue(matrix.contains("`scripts/smoke-spring-boot-real-native`"));
        assertTrue(matrix.contains("`scripts/smoke-spring-boot-native-expanded-native-zolt`"));
        assertTrue(matrix.contains("`scripts/smoke-quarkus-http`"));
        assertTrue(matrix.contains("`scripts/probe-quarkus-test-annotations`"));
        assertTrue(matrix.contains("`scripts/smoke-adoption-easy-medium`"));
        assertTrue(matrix.contains("`scripts/smoke-resolver-public-beta`"));
        assertTrue(matrix.contains("`scripts/smoke-vertx-http`"));
        assertTrue(matrix.contains("`scripts/smoke-vertx-postgres-crud`"));
        assertTrue(matrix.contains("`scripts/smoke-protobuf-grpc-canary`"));

        assertTrue(matrix.contains("Full server startup and HTTP probing remain future work"));
        assertTrue(matrix.contains("not a general protoc plugin execution surface"));
        assertTrue(matrix.contains("supportsQuarkusTestAnnotations=false"));
        assertTrue(matrix.contains("Spring Boot 3.3 on Java 21"));
        assertTrue(matrix.contains("JVM framework gates and Native Image gates are separate evidence"));
        assertTrue(matrix.contains("does not imply broad Maven, Gradle, plugin, framework, or native-image parity"));
    }

    @Test
    void generatedSourceRowsNameObservableContracts() throws IOException {
        String matrix = Files.readString(RepositoryPaths.root().resolve("docs/framework-fixture-matrix.md"));

        assertTrue(matrix.contains("generated-source plan node"));
        assertTrue(matrix.contains("fresh `zolt-owned-openapi` evidence"));
        assertTrue(matrix.contains("generated class in WAR"));
        assertTrue(matrix.contains("stale/missing output diagnostics"));
        assertTrue(matrix.contains("`tool-protobuf` lockfile scope"));
        assertTrue(matrix.contains("generated classes in the packaged jar"));
        assertTrue(matrix.contains("IDE/check/package visibility"));
    }

    @Test
    void docsIndexLinksFrameworkFixtureMatrix() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));

        assertTrue(docsIndex.contains("`framework-fixture-matrix.md`"));
    }
}
