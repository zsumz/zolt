package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackagePlanCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void packagePlanPrintsSpringBootWarDependencyDispositions() throws IOException {
        Path projectDir = tempDir.resolve("package-plan-boot-war");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("package-plan-boot-war") + """

                [package]
                mode = "spring-boot-war"
                """);
        writePackagePlanLockfile(projectDir, false, true);

        CommandResult result = execute(
                "package",
                "--plan",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Package plan"));
        assertTrue(result.stdout().contains("Mode: spring-boot-war"));
        assertTrue(result.stdout().contains("Application layout: WEB-INF/classes"));
        assertTrue(result.stdout().contains("org.springframework.boot:spring-boot-loader:4.0.6 [runtime] loader -> archive root rule=spring-boot-war-loader-expanded"));
        assertTrue(result.stdout().contains("com.example:runtime-lib:1.0.0 [runtime] included -> WEB-INF/lib/runtime-lib-1.0.0.jar rule=spring-boot-war-runtime-lib"));
        assertTrue(result.stdout().contains("jakarta.servlet:jakarta.servlet-api:6.1.0 [provided] provided -> WEB-INF/lib-provided/jakarta.servlet-api-6.1.0.jar rule=spring-boot-war-provided-lib"));
        assertTrue(result.stdout().contains("com.example:devtools:1.0.0 [dev] omitted rule=dev-only-omitted"));
        assertTrue(result.stdout().contains("jakarta.servlet:jakarta.servlet-api:6.1.0 [provided] provided -> WEB-INF/lib-provided/jakarta.servlet-api-6.1.0.jar rule=spring-boot-war-provided-lib lanes=compile packageDefault=false lane=provided-container"));
        assertTrue(result.stdout().contains("com.example:devtools:1.0.0 [dev] omitted rule=dev-only-omitted lanes=runtime,test packageDefault=false lane=development-only"));
        assertTrue(result.stdout().contains("warning CONTAINER_DEPENDENCY_PACKAGED org.apache.tomcat.embed:tomcat-embed-core:10.1.40 rule=spring-boot-war-runtime-lib"));
        assertFalse(Files.exists(projectDir.resolve("target/package-plan-boot-war-0.1.0.war")));
    }

    @Test
    void packagePlanPrintsQuarkusDependencyDispositions() throws IOException {
        Path projectDir = tempDir.resolve("package-plan-quarkus");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("package-plan-quarkus") + """

                [package]
                mode = "quarkus"

                [framework.quarkus]
                enabled = true
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "io.quarkus:quarkus-rest"
                version = "3.33.0"
                source = "maven-central"
                scope = "runtime"
                direct = true
                jar = "io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar"
                dependencies = []

                [[package]]
                id = "io.quarkus:quarkus-rest-deployment"
                version = "3.33.0"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar"
                dependencies = []
                """);

        CommandResult result = execute(
                "package",
                "--plan",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Mode: quarkus"));
        assertTrue(result.stdout().contains(
                "Archive: " + projectDir.resolve("target/quarkus-app/quarkus-run.jar")));
        assertTrue(result.stdout().contains("Application layout: target/quarkus-app/app"));
        assertTrue(result.stdout().contains(
                "io.quarkus:quarkus-rest:3.33.0 [runtime] included -> target/quarkus-app/lib/quarkus-rest-3.33.0.jar rule=quarkus-runtime-lib"));
        assertTrue(result.stdout().contains(
                "io.quarkus:quarkus-rest-deployment:3.33.0 [quarkus-deployment] omitted rule=quarkus-deployment-omitted"));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus-app/quarkus-run.jar")));
    }

    @Test
    void packagePlanJsonUsesStableShapeForThinJar() throws IOException {
        Path projectDir = tempDir.resolve("package-plan-json");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("package-plan-json"));
        writePackagePlanLockfile(projectDir, true, false);

        CommandResult result = execute(
                "package",
                "--plan",
                "--format", "json",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("{\n"));
        assertTrue(result.stdout().contains("\"mode\": \"thin\""));
        assertTrue(result.stdout().contains("\"runtimeClasspath\": \"" + projectDir.resolve("target/package-plan-json-0.1.0.runtime-classpath")));
        assertTrue(result.stdout().contains("\"coordinate\": \"com.example:runtime-lib:1.0.0\""));
        assertTrue(result.stdout().contains("\"lanes\": [\"runtime\", \"test\"]"));
        assertTrue(result.stdout().contains("\"packageDefault\": true"));
        assertTrue(result.stdout().contains("\"laneDisposition\": \"package-default\""));
        assertTrue(result.stdout().contains("\"coordinate\": \"com.example:devtools:1.0.0\""));
        assertTrue(result.stdout().contains("\"packageDefault\": false"));
        assertTrue(result.stdout().contains("\"laneDisposition\": \"development-only\""));
        assertTrue(result.stdout().contains("\"disposition\": \"runtime-classpath\""));
        assertTrue(result.stdout().contains("\"rule\": \"thin-runtime-classpath\""));
        assertTrue(result.stdout().contains("\"policies\": [\"strict-version: com.example:runtime-lib -> 1.0.0 (security baseline)\"]"));
        assertTrue(result.stdout().contains("\"warnings\": []"));
    }

    private static void writePackagePlanLockfile(
            Path projectDir,
            boolean includePolicy,
            boolean includeSuspiciousContainerRuntime) throws IOException {
        String policy = includePolicy
                ? """
                policies = ["strict-version: com.example:runtime-lib -> 1.0.0 (security baseline)"]
                """
                : "";
        String suspiciousContainer = includeSuspiciousContainerRuntime
                ? """

                [[package]]
                id = "org.apache.tomcat.embed:tomcat-embed-core"
                version = "10.1.40"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "org/apache/tomcat/embed/tomcat-embed-core/10.1.40/tomcat-embed-core-10.1.40.jar"
                dependencies = []
                """
                : "";
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.springframework.boot:spring-boot"
                version = "4.0.6"
                source = "maven-central"
                scope = "compile"
                direct = false
                jar = "org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "org.springframework.boot:spring-boot-loader"
                version = "4.0.6"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []
                %s

                [[package]]
                id = "jakarta.servlet:jakarta.servlet-api"
                version = "6.1.0"
                source = "maven-central"
                scope = "provided"
                direct = true
                jar = "jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:devtools"
                version = "1.0.0"
                source = "maven-central"
                scope = "dev"
                direct = true
                jar = "com/example/devtools/1.0.0/devtools-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:test-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "com/example/test-lib/1.0.0/test-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "com/example/processor/1.0.0/processor-1.0.0.jar"
                dependencies = []
                %s
                """.formatted(policy, suspiciousContainer));
    }
}
