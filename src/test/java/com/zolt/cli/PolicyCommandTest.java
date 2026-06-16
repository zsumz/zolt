package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PolicyCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void policyPrintsDependencyBaselineDiagnostics() throws IOException {
        Path projectDir = tempDir.resolve("policy-text");
        PolicyCommandTestSupport.writePolicyProject(projectDir);
        PolicyCommandTestSupport.writePolicyLockfile(projectDir);

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
        PolicyCommandTestSupport.writePolicyProject(projectDir);
        PolicyCommandTestSupport.writePolicyLockfile(projectDir);

        CliTestSupport.CommandResult result = execute("policy", "--format", "json", "--cwd", projectDir.toString());

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
}
