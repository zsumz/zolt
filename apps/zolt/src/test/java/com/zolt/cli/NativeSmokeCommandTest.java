package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeSmokeCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void nativeSmokeKeepsReleaseArchiveOutputProjectRelativeFromCli() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        NativeCommandTestSupport.writeProjectConfigWithMain(projectDir, "https://repo.maven.apache.org/maven2");
        Path binary = NativeCommandTestSupport.writeFakeNativeBinary(projectDir);

        CommandResult result = execute(
                "native-smoke",
                "--cwd", projectDir.toString(),
                "--binary", Path.of("target/native/zolt").toString(),
                "--work-dir", projectDir.resolve("target/native-smoke").toAbsolutePath().normalize().toString());

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("Native smoke status: ok"));
        assertTrue(Files.exists(projectDir.resolve("target/native-smoke/release/demo-0.1.0-linux-x64.tar.gz")));
        assertTrue(Files.exists(binary));
    }

    @Test
    void nativeSmokeDefaultsWorkDirectoryFromConfiguredOutputRoot() throws IOException {
        Path projectDir = tempDir.resolve("demo-output-root");
        NativeCommandTestSupport.writeProjectConfigWithMain(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(
                projectDir.resolve("zolt.toml"),
                Files.readString(projectDir.resolve("zolt.toml"))
                        .replace("[build]\n", "[build]\n                outputRoot = \".zolt/build\"\n"));
        NativeCommandTestSupport.writeFakeNativeBinary(projectDir);

        CommandResult result = execute(
                "native-smoke",
                "--cwd", projectDir.toString(),
                "--binary", Path.of("target/native/zolt").toString());

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(Files.exists(projectDir.resolve(".zolt/build/native-smoke/release/demo-0.1.0-linux-x64.tar.gz")));
    }
}
