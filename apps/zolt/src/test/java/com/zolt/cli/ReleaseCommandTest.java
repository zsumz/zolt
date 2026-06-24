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

final class ReleaseCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void releaseArchiveHelpShowsDirectoryOption() {
        CommandResult result = execute("release-archive", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("--directory"));
        assertTrue(result.stdout().contains("Run as if Zolt was started in the given project"));
        assertTrue(result.stdout().contains("directory."));
        assertEquals("", result.stderr());
    }

    @Test
    void releaseArchiveAssemblesArchiveFromNativeBinary() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("README.md"), "# Demo\n");
        Path binary = projectDir.resolve("target/native/demo");
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, "native");

        CommandResult result = execute(
                "--progress=always",
                "release-archive",
                "--directory", projectDir.toString(),
                "--target", "linux-x64");

        Path archive = projectDir.resolve("dist/demo-0.1.0-linux-x64.tar.gz");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Assembled linux-x64 release archive"));
        assertTrue(result.stdout().contains("Included 3 files under demo-0.1.0-linux-x64"));
        assertTrue(result.stdout().contains("Wrote archive to " + archive));
        assertTrue(result.stdout().contains("Wrote checksum to " + archive + ".sha256"));
        assertTrue(result.stdout().contains("Wrote manifest to " + projectDir.resolve("dist/release-manifest.json")));
        assertTrue(result.stderr().contains("Assembling release archive..."));
        assertTrue(result.stderr().contains("Assembled linux-x64 release archive"));
        assertTrue(Files.exists(archive));
        assertTrue(Files.exists(projectDir.resolve("dist/demo-0.1.0-linux-x64.tar.gz.sha256")));
        assertTrue(Files.exists(projectDir.resolve("dist/release-manifest.json")));
    }

    @Test
    void releaseArchiveReportsMissingBinaryClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "release-archive",
                "--cwd", projectDir.toString(),
                "--target", "linux-x64");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Release archive requires native binary"));
        assertTrue(result.stderr().contains("Run `zolt native` or pass --binary <path>"));
    }

    @Test
    void releaseVerifyHelpShowsDirectoryOption() {
        CommandResult result = execute("release-verify", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("--directory"));
        assertTrue(result.stdout().contains("Run as if Zolt was started in the given project"));
        assertTrue(result.stdout().contains("directory."));
        assertEquals("", result.stderr());
    }

    @Test
    void releaseVerifyReportsMissingArchiveClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "release-verify",
                "--cwd", projectDir.toString(),
                "dist/missing.tar.gz");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Release archive verification failed for"));
        assertTrue(result.stderr().contains("archive does not exist"));
        assertTrue(result.stderr().contains("Pass a valid release archive path"));
    }

    @Test
    void releaseVerifyDefaultsWorkDirectoryFromConfiguredOutputRoot() throws IOException {
        Path projectDir = tempDir.resolve("demo-output-root");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(
                projectDir.resolve("zolt.toml"),
                Files.readString(projectDir.resolve("zolt.toml"))
                        .replace("[build]\n", "[build]\n                outputRoot = \".zolt/build\"\n"));

        CommandResult result = execute(
                "release-verify",
                "--directory", projectDir.toString(),
                "dist/missing.tar.gz");

        assertEquals(1, result.exitCode());
        assertTrue(Files.isDirectory(projectDir.resolve(".zolt/build/release-verify")));
        assertTrue(result.stderr().contains("archive does not exist"));
    }

    private static void writeProjectConfig(Path projectDir, String repositoryUrl) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                test = "%s"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(currentJavaMajorVersion(), repositoryUrl));
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
