package sh.zolt.lockfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class LockDependencyEdgeTest {
    private static final PackageId NETTY = new PackageId("io.netty", "netty-transport-native-epoll");

    @Test
    void defaultVariantEncodesToBareGavUnchanged() {
        LockDependencyEdge edge = new LockDependencyEdge(
                NETTY, "4.1.100.Final", new LockArtifactVariant("jar", Optional.empty()));

        assertEquals("io.netty:netty-transport-native-epoll:4.1.100.Final", edge.encode());
        assertEquals("io.netty:netty-transport-native-epoll:4.1.100.Final", edge.gav());
    }

    @Test
    void classifiedVariantAppendsKeyAsFourthField() {
        LockDependencyEdge edge = new LockDependencyEdge(
                NETTY, "4.1.100.Final", new LockArtifactVariant("jar", Optional.of("linux-x86_64")));

        assertEquals("io.netty:netty-transport-native-epoll:4.1.100.Final:jar|linux-x86_64", edge.encode());
        assertEquals("io.netty:netty-transport-native-epoll:4.1.100.Final", edge.gav());
    }

    @Test
    void nonJarExtensionAppendsExtensionOnly() {
        LockDependencyEdge edge = new LockDependencyEdge(
                new PackageId("g", "a"), "1.0", new LockArtifactVariant("zip", Optional.empty()));

        assertEquals("g:a:1.0:zip", edge.encode());
    }

    @Test
    void parseRoundTripsBareAndQualifiedEdges() {
        for (String wire : List.of(
                "io.netty:netty:4.1.100.Final",
                "io.netty:netty:4.1.100.Final:jar|linux-x86_64",
                "g:a:1.0:zip",
                "g:a:1.0:war|classes")) {
            assertEquals(wire, LockDependencyEdge.parse(wire).orElseThrow().encode());
        }
    }

    @Test
    void parseRejectsMalformedShapes() {
        assertTrue(LockDependencyEdge.parse("g:a").isEmpty());
        assertTrue(LockDependencyEdge.parse("g:a:1.0:jar|c:extra").isEmpty());
        assertTrue(LockDependencyEdge.parse("g:a:").isEmpty());
    }

    @Test
    void ofDerivesTheEdgeThatPointsAtAPackage() {
        LockPackage classified = jarPackage(
                NETTY, "4.1.90.Final",
                "io/netty/netty-transport-native-epoll/4.1.90.Final/"
                        + "netty-transport-native-epoll-4.1.90.Final-linux-x86_64.jar");

        assertEquals(
                "io.netty:netty-transport-native-epoll:4.1.90.Final:jar|linux-x86_64",
                LockDependencyEdge.of(classified).encode());
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
