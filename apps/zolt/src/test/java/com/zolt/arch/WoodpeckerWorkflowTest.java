package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WoodpeckerWorkflowTest {
    private static final Path WOODPECKER = RepositoryPaths.root().resolve(".woodpecker");

    @Test
    void heavyPipelinesUseRequiredLabelShape() throws IOException {
        assertHasHeavyLabel("jvm.yml");
        assertHasHeavyLabel("native.yml");
        assertHasHeavyLabel("perf-cold-resolve.yml");
        assertHasHeavyLabel("vertx-postgres.yml");
    }

    @Test
    void vertxPostgresPipelineUsesRepoScriptsAndPostgresService() throws IOException {
        String workflow = Files.readString(WOODPECKER.resolve("vertx-postgres.yml"));

        assertTrue(workflow.contains("event: manual"));
        assertTrue(workflow.contains("services:"));
        assertTrue(workflow.contains("postgres:"));
        assertTrue(workflow.contains("image: postgres:16-alpine"));
        assertTrue(workflow.contains("ZOLT_VERTX_POSTGRES_HOST: postgres"));
        assertTrue(workflow.contains("ZOLT_VERTX_POSTGRES_PORT: \"5432\""));
        assertTrue(workflow.contains("ZOLT_VERTX_POSTGRES_DATABASE: zolt_vertx"));
        assertTrue(workflow.contains("ZOLT_VERTX_POSTGRES_USER: zolt"));
        assertTrue(workflow.contains("ZOLT_VERTX_POSTGRES_PASSWORD: zolt"));
        assertTrue(workflow.contains("scripts/ci-ensure-curl"));
        assertTrue(workflow.contains("ZOLT_VERTX_POSTGRES_SMOKE_ZOLT=scripts/bootstrap-zolt-jvm scripts/smoke-vertx-postgres-crud"));
        assertTrue(workflow.contains("ZOLT_VERTX_POSTGRES_NATIVE_SMOKE_ZOLT=scripts/bootstrap-zolt-jvm scripts/smoke-vertx-postgres-native"));
        assertTrue(workflow.contains("depends_on:"));
        assertTrue(workflow.contains("- vertx_postgres_jvm_smoke"));
        assertTrue(workflow.contains("vertx_postgres_jvm_logs:"));
        assertTrue(workflow.contains("target/vertx-postgres-crud-smoke/*.txt"));
        assertTrue(workflow.contains("target/vertx-postgres-crud-smoke/*.json"));
        assertTrue(workflow.contains("target/vertx-postgres-crud-smoke/*.headers"));
        assertTrue(workflow.contains("target/vertx-postgres-crud-smoke/*.body"));
        assertTrue(workflow.contains("vertx_postgres_native_logs:"));
        assertTrue(workflow.contains("target/vertx-postgres-native-smoke/*.json"));
        assertTrue(workflow.contains("target/vertx-postgres-native-smoke/*.headers"));
        assertTrue(workflow.contains("target/vertx-postgres-native-smoke/*.body"));
        assertTrue(workflow.contains("target/vertx-postgres-native-smoke/*/target/native/native-image.log"));
    }

    @Test
    void jvmPipelineRunsVertxPostgresSmokeScriptTests() throws IOException {
        String workflow = Files.readString(WOODPECKER.resolve("jvm.yml"));

        assertTrue(workflow.contains("scripts/ci-ensure-curl-test"));
        assertTrue(workflow.contains("scripts/smoke-vertx-postgres-common-test"));
        assertTrue(workflow.contains("scripts/smoke-vertx-postgres-crud-test"));
        assertTrue(workflow.contains("scripts/smoke-vertx-postgres-native-test"));
    }

    private static void assertHasHeavyLabel(String fileName) throws IOException {
        String workflow = Files.readString(WOODPECKER.resolve(fileName));

        assertTrue(
                workflow.contains("""
                        labels:
                          type: heavy
                        """),
                fileName + " must use Woodpecker heavy label shape");
    }
}
