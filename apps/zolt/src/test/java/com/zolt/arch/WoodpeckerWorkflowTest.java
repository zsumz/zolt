package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(workflow.contains("depends_on:"));
        assertTrue(workflow.contains("- vertx_postgres_jvm_smoke"));
        assertTrue(workflow.contains("vertx_postgres_jvm_logs:"));
        assertTrue(workflow.contains("target/vertx-postgres-crud-smoke/*.txt"));
        assertTrue(workflow.contains("target/vertx-postgres-crud-smoke/*.json"));
        assertTrue(workflow.contains("target/vertx-postgres-crud-smoke/*.headers"));
        assertTrue(workflow.contains("target/vertx-postgres-crud-smoke/*.body"));
        assertTrue(workflow.contains("vertx_postgres_native_smoke:"));
        assertTrue(workflow.contains("image: ghcr.io/graalvm/native-image-community:21"));
        assertTrue(workflow.contains("ZOLT_VERTX_POSTGRES_NATIVE_SMOKE_ZOLT=scripts/bootstrap-zolt-jvm scripts/smoke-vertx-postgres-native"));
        assertTrue(workflow.contains("- vertx_postgres_jvm_smoke"));
        assertTrue(workflow.contains("vertx_postgres_native_logs:"));
        assertTrue(workflow.contains("target/vertx-postgres-native-smoke"));
        assertTrue(workflow.contains("target/vertx-postgres-native-smoke/vertx-postgres-crud/target/native/native-image.log"));
    }

    @Test
    void manualNativeEntryPointsUseNativeZoltEvidence() throws IOException {
        String nativeWorkflow = Files.readString(WOODPECKER.resolve("native.yml"));
        String coldResolveWorkflow = Files.readString(WOODPECKER.resolve("perf-cold-resolve.yml"));

        assertTrue(nativeWorkflow.contains("event: manual"));
        assertTrue(coldResolveWorkflow.contains("native_self_host:"));
        assertTrue(coldResolveWorkflow.contains("scripts/self-host-native"));
        assertTrue(coldResolveWorkflow.contains("scripts/perf-cold-resolve-gate --repeat"));
        assertTrue(coldResolveWorkflow.contains("- native_self_host"));
        assertFalse(coldResolveWorkflow.contains("--zolt-tool zolt-jvm"));
        assertFalse(coldResolveWorkflow.contains("jvm_self_host:"));
    }

    @Test
    void jvmPipelineRunsVertxPostgresSmokeScriptTests() throws IOException {
        String workflow = Files.readString(WOODPECKER.resolve("jvm.yml"));

        assertTrue(workflow.contains("scripts/ci-ensure-curl-test"));
        assertTrue(workflow.contains("scripts/smoke-spring-boot-petclinic-resources-test"));
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
