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
    void nativeSmokeHelpShowsDirectoryOption() {
        CommandResult result = execute("native-smoke", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("--directory"));
        assertTrue(result.stdout().contains("Run as if Zolt was started in the given project"));
        assertTrue(result.stdout().contains("directory."));
        assertEquals("", result.stderr());
    }

    @Test
    void nativeSmokeKeepsReleaseArchiveOutputProjectRelativeFromCli() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        NativeCommandTestSupport.writeProjectConfigWithMain(projectDir, "https://repo.maven.apache.org/maven2");
        Path binary = NativeCommandTestSupport.writeFakeNativeBinary(projectDir);

        CommandResult result = execute(
                "native-smoke",
                "--directory", projectDir.toString(),
                "--binary", Path.of("target/native/zolt").toString(),
                "--work-dir", projectDir.resolve("target/native-smoke").toAbsolutePath().normalize().toString());

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("Native smoke status: ok"));
        assertTrue(Files.exists(projectDir.resolve("target/native-smoke/release/demo-0.1.0-linux-x64.tar.gz")));
        assertTrue(Files.exists(binary));
    }

    @Test
    void nativeSmokeUsesModernHumanOutputControls() throws IOException {
        Path colorProject = tempDir.resolve("color-smoke");
        NativeCommandTestSupport.writeProjectConfigWithMain(colorProject, "https://repo.maven.apache.org/maven2");
        NativeCommandTestSupport.writeFakeNativeBinary(colorProject);
        Path quietProject = tempDir.resolve("quiet-smoke");
        NativeCommandTestSupport.writeProjectConfigWithMain(quietProject, "https://repo.maven.apache.org/maven2");
        NativeCommandTestSupport.writeFakeNativeBinary(quietProject);

        CommandResult color = execute(
                "--color=always",
                "native-smoke",
                "--directory", colorProject.toString(),
                "--binary", Path.of("target/native/zolt").toString());
        CommandResult quiet = execute(
                "--quiet",
                "native-smoke",
                "--directory", quietProject.toString(),
                "--binary", Path.of("target/native/zolt").toString());

        assertEquals(0, color.exitCode(), color.stderr());
        assertTrue(color.stdout().contains("\u001B[32mNative\u001B[0m smoke status: ok"));
        assertTrue(color.stdout().contains("\u001B[32mSmoked\u001B[0m binary"));
        assertEquals(0, quiet.exitCode(), quiet.stderr());
        assertEquals("", quiet.stdout());
        assertTrue(Files.exists(colorProject.resolve("target/native-smoke/release/demo-0.1.0-linux-x64.tar.gz")));
        assertTrue(Files.exists(quietProject.resolve("target/native-smoke/release/demo-0.1.0-linux-x64.tar.gz")));
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
