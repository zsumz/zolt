package sh.zolt.cli.command.build;

import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.toolchain.ManagedJavaToolchainTestFixture;
import sh.zolt.toolchain.lock.LockedJavaToolchain;
import sh.zolt.toolchain.lock.ToolchainLockfileService;
import sh.zolt.toolchain.store.ToolchainStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RunCommandToolchainTest {
    @TempDir
    private Path tempDir;

    @Test
    void runBuildsAndLaunchesWithManagedJavaToolchain() throws IOException {
        Path projectDir = ManagedJavaToolchainTestFixture.writeProject(tempDir, "managed-run-demo");
        ToolchainStore store = new ToolchainStore(tempDir.resolve("toolchains"));
        LockedJavaToolchain locked = ManagedJavaToolchainTestFixture.locked();
        new ToolchainLockfileService().writeJava(projectDir.resolve("zolt.lock"), locked);
        Path javacMarker = projectDir.resolve("target/javac-marker.txt");
        ManagedJavaToolchainTestFixture.installManagedToolchain(store, locked, javacMarker);

        var result = execute(
                "run",
                "--directory", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--toolchain-target", "linux-x64",
                "--toolchain-install-root", tempDir.resolve("toolchains").toString(),
                "--",
                "hello");

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
        assertTrue(Files.readString(javacMarker).contains("javac=" + store.javac(locked)));
        assertTrue(result.stdout().contains("java=" + store.java(locked)), result.stdout());
        assertTrue(result.stdout().contains("args=-classpath"), result.stdout());
        assertTrue(result.stdout().contains("hello"), result.stdout());
    }

    @Test
    void runFailsClearlyWhenStrictManagedToolchainIsMissing() throws IOException {
        Path projectDir = ManagedJavaToolchainTestFixture.writeProject(tempDir, "missing-run-demo");

        var result = execute(
                "run",
                "--directory", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--toolchain-target", "linux-x64",
                "--toolchain-install-root", tempDir.resolve("missing-toolchains").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Java toolchain is not ready for run"));
        assertTrue(result.stderr().contains("zolt toolchain status"));
        assertTrue(result.stderr().contains("zolt toolchain sync"));
        assertTrue(Files.notExists(projectDir.resolve("target/classes/com/example/Main.class")));
    }
}
