package sh.zolt.toolchain.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.toolchain.JavaDistribution;
import sh.zolt.project.toolchain.JavaFeature;
import sh.zolt.project.toolchain.JavaToolchainRequest;
import sh.zolt.project.toolchain.ToolchainPolicy;
import sh.zolt.toolchain.platform.HostPlatform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ToolchainLockfileServiceTest {
    @TempDir
    private Path tempDir;

    private final ToolchainLockfileService lockfiles = new ToolchainLockfileService();

    @Test
    void appendsJavaToolchainLockWithoutDisturbingPackages() throws IOException {
        Path lockfile = tempDir.resolve("zolt.lock");
        Files.writeString(lockfile, """
                version = 1

                [[package]]
                id = "com.example:demo"
                version = "1.0.0"
                source = "maven"
                scope = "compile"
                direct = true
                dependencies = []
                """);
        LockedJavaToolchain locked = locked("linux-x64");

        lockfiles.writeJava(lockfile, locked);

        String content = Files.readString(lockfile);
        assertTrue(content.contains("[[package]]"));
        assertTrue(content.contains("[[toolchain.java]]"));
        assertTrue(content.contains("request.distribution = \"graalvm-community\""));
        assertTrue(content.contains("artifact.uri = \"https://example.com/graalvm.tar.gz\""));
        assertTrue(content.contains("artifact.sha256 = \"abc123\""));
        assertTrue(content.contains("layout.executables.nativeImage = \"bin/native-image\""));
        List<LockedJavaToolchain> read = lockfiles.readJava(lockfile);
        assertEquals(1, read.size());
        assertEquals("java-graalvm-community-21-native-image", read.getFirst().id());
        assertEquals("https://example.com/graalvm.tar.gz", read.getFirst().artifactUri());
        assertEquals("abc123", read.getFirst().artifactSha256());
    }

    @Test
    void replacesPreviousJavaToolchainLock() throws IOException {
        Path lockfile = tempDir.resolve("zolt.lock");
        Files.writeString(lockfile, "version = 1\n\n");

        lockfiles.writeJava(lockfile, locked("linux-x64"));
        lockfiles.writeJava(lockfile, locked("macos-aarch64"));

        String content = Files.readString(lockfile);
        assertEquals(1, content.split("\\[\\[toolchain\\.java]]", -1).length - 1);
        assertEquals("macos-aarch64", lockfiles.readJava(lockfile).getFirst().platform().id());
    }

    @Test
    void writesDeterministicJavaToolchainPlatformMatrix() throws IOException {
        Path lockfile = tempDir.resolve("zolt.lock");
        Files.writeString(lockfile, """
                version = 1

                [[package]]
                id = "com.example:demo"
                version = "1.0.0"
                source = "maven"
                scope = "compile"
                direct = true
                dependencies = []

                [[toolchain.java]]
                id = "stale"
                request.version = "17"
                request.distribution = "temurin"
                request.features = []
                request.policy = "prefer-managed"
                platform.os = "linux"
                platform.arch = "x64"
                resolved.version = "17"
                resolved.distribution = "temurin"
                artifact.catalog = "stale"
                layout.javaHome = "."
                layout.executables.java = "bin/java"
                layout.executables.javac = "bin/javac"
                layout.executables.jar = "bin/jar"
                """);

        lockfiles.writeJava(lockfile, List.of(
                locked("macos-aarch64"),
                locked("linux-aarch64"),
                locked("macos-x64"),
                locked("linux-x64")));

        String content = Files.readString(lockfile);
        assertTrue(content.contains("[[package]]"));
        assertEquals(4, content.split("\\[\\[toolchain\\.java]]", -1).length - 1);
        assertEquals(4, lockfiles.readJava(lockfile).size());
        assertBefore(content, "platform.arch = \"x64\"", "platform.arch = \"aarch64\"");
        assertBefore(content, "platform.os = \"linux\"", "platform.os = \"macos\"");
        assertTrue(!content.contains("id = \"stale\""));
    }

    private static void assertBefore(String content, String first, String second) {
        assertTrue(content.indexOf(first) >= 0, first);
        assertTrue(content.indexOf(second) >= 0, second);
        assertTrue(content.indexOf(first) < content.indexOf(second), first + " should appear before " + second);
    }

    private static LockedJavaToolchain locked(String target) {
        JavaToolchainRequest request = new JavaToolchainRequest(
                "21",
                JavaDistribution.GRAALVM_COMMUNITY,
                Set.of(JavaFeature.NATIVE_IMAGE),
                ToolchainPolicy.PREFER_MANAGED);
        return new LockedJavaToolchain(
                "java-graalvm-community-21-native-image",
                request,
                HostPlatform.parse(target),
                "21",
                JavaDistribution.GRAALVM_COMMUNITY,
                "builtin:java-graalvm-community-21-native-image",
                "https://example.com/graalvm.tar.gz",
                "abc123",
                JavaToolchainLayout.standard(true));
    }
}
