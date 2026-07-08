package sh.zolt.toolchain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.toolchain.JavaDistribution;
import sh.zolt.project.toolchain.JavaFeature;
import sh.zolt.project.toolchain.JavaToolchainRequest;
import sh.zolt.project.toolchain.ToolchainPolicy;
import sh.zolt.toolchain.lock.LockedJavaToolchain;
import sh.zolt.toolchain.platform.HostPlatform;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BundledJavaToolchainCatalogTest {
    private final BundledJavaToolchainCatalog catalog = new BundledJavaToolchainCatalog();

    @Test
    void locksGraalVmJavaTwentyOneNativeImageLane() {
        Optional<LockedJavaToolchain> locked = catalog.lock(
                new JavaToolchainRequest(
                        "21",
                        JavaDistribution.GRAALVM_COMMUNITY,
                        Set.of(JavaFeature.NATIVE_IMAGE),
                        ToolchainPolicy.PREFER_MANAGED),
                HostPlatform.parse("linux-x64"));

        LockedJavaToolchain java = locked.orElseThrow();
        assertEquals("java-graalvm-community-21-native-image", java.id());
        assertEquals("builtin:java-graalvm-community-21-native-image", java.catalog());
        assertEquals("lib/svm/bin/native-image", java.layout().nativeImage());
        JavaToolchainArtifact artifact = catalog.artifact(java).orElseThrow();
        assertEquals(JavaToolchainArchiveFormat.TAR_GZ, artifact.format());
        assertTrue(artifact.uri().toString().contains("graalvm-community-jdk-21.0.2_linux-x64_bin.tar.gz"));
    }

    @Test
    void locksMacArchivesUnderContentsHome() {
        LockedJavaToolchain locked = catalog.lock(
                        new JavaToolchainRequest(
                                "21",
                                JavaDistribution.GRAALVM_COMMUNITY,
                                Set.of(JavaFeature.NATIVE_IMAGE),
                                ToolchainPolicy.PREFER_MANAGED),
                        HostPlatform.parse("macos-aarch64"))
                .orElseThrow();

        assertEquals("Contents/Home", locked.layout().javaHome());
        assertEquals("bin/java", locked.layout().java());
        assertEquals("lib/svm/bin/native-image", locked.layout().nativeImage());
    }

    @Test
    void locksStableUnixPlatformMatrix() {
        List<LockedJavaToolchain> locked = catalog.locks(
                new JavaToolchainRequest(
                        "21",
                        JavaDistribution.GRAALVM_COMMUNITY,
                        Set.of(JavaFeature.NATIVE_IMAGE),
                        ToolchainPolicy.PREFER_MANAGED),
                HostPlatform.parse("macos-aarch64"));

        assertEquals(List.of("linux-x64", "linux-aarch64", "macos-x64", "macos-aarch64"), locked.stream()
                .map(java -> java.platform().id())
                .toList());
    }

    @Test
    void rejectsUnsupportedWindowsToolchainTargetsForNow() {
        Optional<LockedJavaToolchain> locked = catalog.lock(
                new JavaToolchainRequest(
                        "21",
                        JavaDistribution.GRAALVM_COMMUNITY,
                        Set.of(JavaFeature.NATIVE_IMAGE),
                        ToolchainPolicy.PREFER_MANAGED),
                HostPlatform.parse("windows-x64"));

        assertTrue(locked.isEmpty());
    }

    @Test
    void rejectsTemurinNativeImageLaneUntilThereIsAnAdapter() {
        Optional<LockedJavaToolchain> locked = catalog.lock(
                new JavaToolchainRequest(
                        "21",
                        JavaDistribution.TEMURIN,
                        Set.of(JavaFeature.NATIVE_IMAGE),
                        ToolchainPolicy.PREFER_MANAGED),
                HostPlatform.parse("linux-x64"));

        assertTrue(locked.isEmpty());
    }

    @Test
    void resolvesTemurinArtifactThroughAdoptiumApi() {
        LockedJavaToolchain locked = catalog.lock(
                        new JavaToolchainRequest(
                                "21",
                                JavaDistribution.TEMURIN,
                                Set.of(),
                                ToolchainPolicy.PREFER_MANAGED),
                        HostPlatform.parse("macos-aarch64"))
                .orElseThrow();

        JavaToolchainArtifact artifact = catalog.artifact(locked).orElseThrow();

        assertEquals(JavaToolchainArchiveFormat.TAR_GZ, artifact.format());
        assertTrue(artifact.uri().toString().contains("/v3/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse"));
    }
}
