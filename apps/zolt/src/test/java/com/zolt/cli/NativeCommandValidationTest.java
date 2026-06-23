package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeCommandValidationTest {
    @TempDir
    private Path tempDir;

    @Test
    void nativeReportsMissingMainClassClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        NativeCommandTestSupport.writeProjectConfigWithoutMain(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "native",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Native Image main class is missing"));
        assertTrue(result.stderr().contains("[project].main"));
    }

    @Test
    void nativeReportsSpringBootNativeRequiresExplicitAotFlag() throws IOException {
        Path projectDir = tempDir.resolve("spring-boot-demo");
        NativeCommandTestSupport.writeSpringBootProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "native",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Spring Boot native images require `[framework.springBoot.native] enabled = true`"));
        assertTrue(result.stderr().contains("Spring Boot JVM build, test, run, and executable packaging"));
        assertTrue(result.stderr().contains("explicit Zolt-owned Spring Boot AOT/native canary path"));
        assertTrue(result.stderr().contains("zolt package --mode spring-boot"));
        assertFalse(result.stderr().contains("not supported by Zolt yet"));
    }

    @Test
    void nativeReportsMissingSpringBootAotToolingClearly() throws IOException {
        Path projectDir = tempDir.resolve("spring-boot-native-demo");
        NativeCommandTestSupport.writeExplicitSpringBootNativeProjectConfig(
                projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "native",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Spring Boot native AOT requires tool artifact"));
        assertTrue(result.stderr().contains("Add the Spring Boot platform to [platforms]"));
    }
}
