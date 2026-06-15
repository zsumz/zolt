package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PolicyCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void policyPrintsDependencyBaselineDiagnostics() throws IOException {
        Path projectDir = tempDir.resolve("policy-text");
        writePolicyProject(projectDir);
        writePolicyLockfile(projectDir);

        CommandResult result = execute("policy", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Dependency policy diagnostics"));
        assertTrue(result.stdout().contains("Platforms: 1"));
        assertTrue(result.stdout().contains(
                "org.springframework.boot:spring-boot-dependencies:4.0.6 versionRef=spring-boot manages 1 selected packages"));
        assertTrue(result.stdout().contains(
                "org.springframework.boot:spring-boot-starter-web:4.0.6 [compile] managed-version: org.springframework.boot:spring-boot-starter-web -> 4.0.6 from org.springframework.boot:spring-boot-dependencies:4.0.6"));
        assertTrue(result.stdout().contains(
                "org.apache.tomcat.embed:tomcat-embed-core strict 10.1.40 versionRef=tomcat-baseline status=pinned selected=10.1.40 source=org.springframework.boot:spring-boot-starter-web:4.0.6 reason=Container baseline"));
        assertTrue(result.stdout().contains("com.example:unused strict 1.0.0 status=unmatched"));
        assertTrue(result.stdout().contains(
                "com.example:direct-lib status=direct-conflict reason=Direct dependency conflict fixture"));
        assertTrue(result.stdout().contains(
                "commons-logging:commons-logging status=matched reason=Use jcl-over-slf4j"));
        assertTrue(result.stdout().contains("log4j:log4j status=unmatched reason=Legacy logging baseline"));
        assertTrue(result.stdout().contains("dependencies com.example:direct-lib:1.2.3 versionRef=direct-lib status=selected"));
        assertEquals("", result.stderr());
    }

    @Test
    void policyPrintsDeterministicJson() throws IOException {
        Path projectDir = tempDir.resolve("policy-json");
        writePolicyProject(projectDir);
        writePolicyLockfile(projectDir);

        CommandResult result = execute("policy", "--format", "json", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("{\n  \"projectRoot\": \""));
        assertTrue(result.stdout().contains("\"platform\": \"org.springframework.boot:spring-boot-dependencies:4.0.6\""));
        assertTrue(result.stdout().contains("\"versionRef\": \"spring-boot\""));
        assertTrue(result.stdout().contains("\"coordinate\": \"org.apache.tomcat.embed:tomcat-embed-core\""));
        assertTrue(result.stdout().contains("\"versionRef\": \"tomcat-baseline\""));
        assertTrue(result.stdout().contains("\"status\": \"pinned\""));
        assertTrue(result.stdout().contains("\"coordinate\": \"com.example:unused\""));
        assertTrue(result.stdout().contains("\"status\": \"unmatched\""));
        assertTrue(result.stdout().contains("\"coordinate\": \"commons-logging:commons-logging\""));
        assertTrue(result.stdout().contains("\"status\": \"matched\""));
        assertTrue(result.stdout().contains("\"status\": \"direct-conflict\""));
        assertTrue(result.stdout().contains("\"directVersions\": ["));
        assertTrue(result.stdout().contains("\"section\": \"dependencies\""));
        assertTrue(result.stdout().contains("\"versionRef\": \"direct-lib\""));
        assertEquals(result.stdout(), execute("policy", "--format", "json", "--cwd", projectDir.toString()).stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void checkDependencyPolicyPassesWithoutConfiguredPolicy() throws IOException {
        Path projectDir = tempDir.resolve("check-dependency-policy-empty");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-dependency-policy-empty"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--check", "dependency-policy",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "ok dependency-policy check-dependency-policy-empty Dependency policy baseline is explainable: 0 platforms, 0 constraints, 0 exclusions, and 0 direct explicit versions."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkDependencyPolicyReportsDirectExclusionConflicts() throws IOException {
        Path projectDir = tempDir.resolve("check-dependency-policy-direct-conflict");
        writePolicyProject(projectDir);
        writePolicyLockfile(projectDir);

        CommandResult result = execute(
                "check",
                "--check", "dependency-policy",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains(
                "ok dependency-policy demo Dependency policy baseline is explainable: 1 platform, 2 constraints, 3 exclusions, and 1 direct explicit version."));
        assertTrue(result.stdout().contains(
                "error dependency-policy [dependencyPolicy].exclude com.example:direct-lib Dependency policy excludes `com.example:direct-lib`, but that package is still a direct dependency."));
        assertTrue(result.stdout().contains(
                "next: Remove the direct dependency, or remove the exclusion if the dependency is intentional."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspaceDependencyPolicyPassesForSelectedMemberWithoutPolicy() throws IOException {
        Path workspaceDir = tempDir.resolve("check-workspace-dependency-policy-empty");
        Path apiDir = workspaceDir.resolve("apps/api");
        Files.createDirectories(apiDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-workspace-dependency-policy-empty"
                members = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(workspaceDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "apps/api",
                "--check", "dependency-policy",
                "--cwd", workspaceDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "ok dependency-policy apps/api api Dependency policy baseline is explainable: 0 platforms, 0 constraints, 0 exclusions, and 0 direct explicit versions."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspaceDependencyPolicyReportsSelectedMemberConflicts() throws IOException {
        Path workspaceDir = tempDir.resolve("check-workspace-dependency-policy-direct-conflict");
        Path apiDir = workspaceDir.resolve("apps/api");
        writePolicyProject(apiDir);
        writePolicyLockfile(workspaceDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-workspace-dependency-policy-direct-conflict"
                members = ["apps/api"]
                """);

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "apps/api",
                "--check", "dependency-policy",
                "--cwd", workspaceDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains(
                "ok dependency-policy apps/api demo Dependency policy baseline is explainable: 1 platform, 2 constraints, 3 exclusions, and 1 direct explicit version."));
        assertTrue(result.stdout().contains(
                "error dependency-policy apps/api [dependencyPolicy].exclude com.example:direct-lib Dependency policy excludes `com.example:direct-lib`, but that package is still a direct dependency."));
        assertTrue(result.stdout().contains(
                "next: Remove the direct dependency, or remove the exclusion if the dependency is intentional."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspaceDependencyPolicyMalformedLockfileUsesWorkspaceRemediation() throws IOException {
        Path workspaceDir = tempDir.resolve("check-workspace-dependency-policy-malformed-lockfile");
        Path apiDir = workspaceDir.resolve("apps/api");
        Files.createDirectories(apiDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-workspace-dependency-policy-malformed-lockfile"
                members = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(workspaceDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = 42
                """);

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "apps/api",
                "--check", "dependency-policy",
                "--cwd", workspaceDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error dependency-policy apps/api zolt.lock"));
        assertTrue(result.stdout().contains("next: Run `zolt resolve --workspace` to refresh dependency policy evidence."));
        assertEquals("", result.stderr());
    }

    private static void writePolicyProject(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo") + """

                [repositories]
                test = "https://repo.maven.apache.org/maven2"

                [versions]
                "spring-boot" = "4.0.6"
                "direct-lib" = "1.2.3"
                "tomcat-baseline" = "10.1.40"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = { versionRef = "spring-boot" }

                [dependencies]
                "com.example:direct-lib" = { versionRef = "direct-lib" }
                "org.springframework.boot:spring-boot-starter-web" = {}

                [dependencyPolicy]
                exclude = [
                  { group = "com.example", artifact = "direct-lib", reason = "Direct dependency conflict fixture" },
                  { group = "commons-logging", artifact = "commons-logging", reason = "Use jcl-over-slf4j" },
                  { group = "log4j", artifact = "log4j", reason = "Legacy logging baseline" }
                ]

                [dependencyConstraints]
                "com.example:unused" = { version = "1.0.0", kind = "strict", reason = "Unused baseline" }
                "org.apache.tomcat.embed:tomcat-embed-core" = { versionRef = "tomcat-baseline", kind = "strict", reason = "Container baseline" }

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);
    }

    private static void writePolicyLockfile(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:direct-lib"
                version = "1.2.3"
                source = "maven-central"
                scope = "compile"
                direct = true
                policies = ["version-ref: com.example:direct-lib -> 1.2.3 from [versions].direct-lib"]
                dependencies = []

                [[package]]
                id = "org.springframework.boot:spring-boot-starter-web"
                version = "4.0.6"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = ["org.apache.tomcat.embed:tomcat-embed-core:10.1.40"]
                policies = ["managed-version: org.springframework.boot:spring-boot-starter-web -> 4.0.6 from org.springframework.boot:spring-boot-dependencies:4.0.6"]

                [[package]]
                id = "org.apache.tomcat.embed:tomcat-embed-core"
                version = "10.1.40"
                source = "maven-central"
                scope = "runtime"
                direct = false
                dependencies = []
                policies = ["strict-version: org.apache.tomcat.embed:tomcat-embed-core requested 10.1.39 -> 10.1.40 (Container baseline)"]

                [[policy]]
                kind = "strict-version"
                id = "org.apache.tomcat.embed:tomcat-embed-core"
                requested = "10.1.39"
                source = "org.springframework.boot:spring-boot-starter-web:4.0.6"
                policy = "strict-version: org.apache.tomcat.embed:tomcat-embed-core requested 10.1.39 -> 10.1.40 (Container baseline)"

                [[policy]]
                kind = "global-exclusion"
                id = "commons-logging:commons-logging"
                requested = "1.2"
                source = "org.springframework.boot:spring-boot-starter-web:4.0.6"
                policy = "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)"
                """);
    }
}
