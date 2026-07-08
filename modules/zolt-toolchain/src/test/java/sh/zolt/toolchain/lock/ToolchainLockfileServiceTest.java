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
        assertTrue(content.contains("layout.executables.nativeImage = \"bin/native-image\""));
        List<LockedJavaToolchain> read = lockfiles.readJava(lockfile);
        assertEquals(1, read.size());
        assertEquals("java-graalvm-community-21-native-image", read.getFirst().id());
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
                JavaToolchainLayout.standard(true));
    }
}
