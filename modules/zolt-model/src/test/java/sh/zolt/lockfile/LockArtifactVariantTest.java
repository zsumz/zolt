package sh.zolt.lockfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class LockArtifactVariantTest {
    private static final PackageId EPOLL = new PackageId("io.netty", "netty-transport-native-epoll");

    @Test
    void plainJarHasJarExtensionAndNoClassifier() {
        LockArtifactVariant variant = LockArtifactVariant.of(jarPackage(
                EPOLL,
                "4.1.100.Final",
                "io/netty/netty-transport-native-epoll/4.1.100.Final/netty-transport-native-epoll-4.1.100.Final.jar"));

        assertEquals("jar", variant.extension());
        assertEquals(Optional.empty(), variant.classifier());
        assertEquals("jar", variant.key());
    }

    @Test
    void classifiedJarRecoversClassifierFromJarFilename() {
        LockArtifactVariant variant = LockArtifactVariant.of(jarPackage(
                EPOLL,
                "4.1.100.Final",
                "io/netty/netty-transport-native-epoll/4.1.100.Final/netty-transport-native-epoll-4.1.100.Final-linux-x86_64.jar"));

        assertEquals("jar", variant.extension());
        assertEquals(Optional.of("linux-x86_64"), variant.classifier());
        assertEquals("jar|linux-x86_64", variant.key());
    }

    @Test
    void plainAndTwoClassifiersOfOneGavAreThreeDistinctVariants() {
        LockArtifactVariant plain = LockArtifactVariant.of(jarPackage(
                EPOLL, "4.1.100.Final",
                "io/netty/netty-transport-native-epoll/4.1.100.Final/netty-transport-native-epoll-4.1.100.Final.jar"));
        LockArtifactVariant linux = LockArtifactVariant.of(jarPackage(
                EPOLL, "4.1.100.Final",
                "io/netty/netty-transport-native-epoll/4.1.100.Final/netty-transport-native-epoll-4.1.100.Final-linux-x86_64.jar"));
        LockArtifactVariant osx = LockArtifactVariant.of(jarPackage(
                EPOLL, "4.1.100.Final",
                "io/netty/netty-transport-native-epoll/4.1.100.Final/netty-transport-native-epoll-4.1.100.Final-osx-aarch_64.jar"));

        assertNotEquals(plain, linux);
        assertNotEquals(plain, osx);
        assertNotEquals(linux, osx);
        assertNotEquals(plain.key(), linux.key());
        assertNotEquals(linux.key(), osx.key());
    }

    @Test
    void classifierIsVersionIndependentSoDifferentVersionsShareOneVariant() {
        LockArtifactVariant older = LockArtifactVariant.of(jarPackage(
                EPOLL, "4.1.90.Final",
                "io/netty/netty-transport-native-epoll/4.1.90.Final/netty-transport-native-epoll-4.1.90.Final-linux-x86_64.jar"));
        LockArtifactVariant newer = LockArtifactVariant.of(jarPackage(
                EPOLL, "4.1.100.Final",
                "io/netty/netty-transport-native-epoll/4.1.100.Final/netty-transport-native-epoll-4.1.100.Final-linux-x86_64.jar"));

        assertEquals(older, newer);
        assertEquals(older.key(), newer.key());
    }

    @Test
    void typedArtifactUsesArtifactTypeAsExtension() {
        LockArtifactVariant variant = LockArtifactVariant.of(new LockPackage(
                new PackageId("io.quarkus.platform", "platform-properties"),
                "3.33.0",
                "maven-central",
                DependencyScope.QUARKUS_DEPLOYMENT,
                false,
                Optional.empty(),
                Optional.of("io/quarkus/platform/platform-properties/3.33.0/platform-properties-3.33.0.pom"),
                Optional.empty(),
                Optional.of("pom-sha"),
                Optional.of("io/quarkus/platform/platform-properties/3.33.0/platform-properties-3.33.0.properties"),
                Optional.of("properties"),
                Optional.of("artifact-sha"),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));

        assertEquals("properties", variant.extension());
        assertEquals(Optional.empty(), variant.classifier());
        assertEquals("properties", variant.key());
    }

    @Test
    void workspaceEntryWithoutArtifactFilesIsPlainJarVariant() {
        LockArtifactVariant variant = LockArtifactVariant.of(new LockPackage(
                new PackageId("com.acme", "core"),
                "0.1.0",
                "workspace",
                DependencyScope.COMPILE,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of("modules/core"),
                Optional.of("target/classes"),
                List.of()));

        assertEquals("jar", variant.extension());
        assertEquals(Optional.empty(), variant.classifier());
    }

    @Test
    void comparableOrdersPlainBeforeClassifiedAndByClassifier() {
        LockArtifactVariant plain = new LockArtifactVariant("jar", Optional.empty());
        LockArtifactVariant linux = new LockArtifactVariant("jar", Optional.of("linux-x86_64"));
        LockArtifactVariant osx = new LockArtifactVariant("jar", Optional.of("osx-aarch_64"));

        assertTrue(plain.compareTo(linux) < 0);
        assertTrue(linux.compareTo(osx) < 0);
        assertTrue(osx.compareTo(plain) > 0);
    }

    @Test
    void blankOrNullExtensionNormalizesToJar() {
        assertEquals("jar", new LockArtifactVariant("", Optional.empty()).extension());
        assertEquals("jar", new LockArtifactVariant(null, null).extension());
    }

    @Test
    void onlyPlainJarIsDefault() {
        assertTrue(new LockArtifactVariant("jar", Optional.empty()).isDefault());
        assertTrue(!new LockArtifactVariant("jar", Optional.of("linux-x86_64")).isDefault());
        assertTrue(!new LockArtifactVariant("zip", Optional.empty()).isDefault());
        assertTrue(!new LockArtifactVariant("war", Optional.of("sources")).isDefault());
    }

    @Test
    void fromKeyRoundTripsEveryVariantShape() {
        for (LockArtifactVariant variant : List.of(
                new LockArtifactVariant("jar", Optional.empty()),
                new LockArtifactVariant("jar", Optional.of("linux-x86_64")),
                new LockArtifactVariant("zip", Optional.empty()),
                new LockArtifactVariant("war", Optional.of("classes")))) {
            assertEquals(variant, LockArtifactVariant.fromKey(variant.key()));
        }
    }

    private static LockPackage jarPackage(PackageId packageId, String version, String jarPath) {
        return new LockPackage(
                packageId,
                version,
                "maven-central",
                DependencyScope.RUNTIME,
                false,
                Optional.of(jarPath),
                Optional.of(jarPath.substring(0, jarPath.lastIndexOf('.')) + ".pom"),
                Optional.of("jar-sha"),
                Optional.of("pom-sha"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
