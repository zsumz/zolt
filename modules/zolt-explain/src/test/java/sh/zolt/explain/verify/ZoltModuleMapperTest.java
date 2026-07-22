package sh.zolt.explain.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ZoltModuleMapperTest {
    private final ZoltModuleMapper mapper = new ZoltModuleMapper();

    @Test
    void mapsFourComparedScopesAndNotesTheRest() {
        List<LockPackage> packages = List.of(
                pkg("org.x", "compile-lib", "1.0.0", DependencyScope.COMPILE),
                pkg("org.x", "runtime-lib", "1.1.0", DependencyScope.RUNTIME),
                pkg("org.x", "test-lib", "1.2.0", DependencyScope.TEST),
                pkg("org.x", "provided-lib", "1.3.0", DependencyScope.PROVIDED),
                pkg("org.x", "dev-lib", "1.4.0", DependencyScope.DEV),
                pkg("org.x", "ap", "1.5.0", DependencyScope.PROCESSOR));

        ResolvedModule module = mapper.fromLockPackages("com.example", "app", "9.9", packages);

        assertEquals("com.example:app", module.moduleKey());
        assertEquals(List.of("org.x:compile-lib:1.0.0"), coordinates(module, VerifyScope.COMPILE));
        assertEquals(List.of("org.x:runtime-lib:1.1.0"), coordinates(module, VerifyScope.RUNTIME));
        assertEquals(List.of("org.x:test-lib:1.2.0"), coordinates(module, VerifyScope.TEST));
        assertEquals(List.of("org.x:provided-lib:1.3.0"), coordinates(module, VerifyScope.PROVIDED));
        // dev and processor scopes are outside the compared four and become notes, not comparisons.
        assertEquals(1, module.unmappedScopes().getOrDefault("dev", 0));
        assertEquals(1, module.unmappedScopes().getOrDefault("processor", 0));
    }

    @Test
    void derivesClassifierFromCachePath() {
        assertEquals("linux-x86_64", ZoltModuleMapper.deriveClassifier(
                "netty-transport-native-epoll",
                "4.1.118.Final",
                Optional.of("io/netty/netty-transport-native-epoll/4.1.118.Final/"
                        + "netty-transport-native-epoll-4.1.118.Final-linux-x86_64.jar")));
        assertEquals("", ZoltModuleMapper.deriveClassifier(
                "guava", "33.4.8-jre",
                Optional.of("com/google/guava/guava/33.4.8-jre/guava-33.4.8-jre.jar")));
        assertEquals("", ZoltModuleMapper.deriveClassifier("lib", "1.0.0", Optional.empty()));
    }

    @Test
    void classifierArtifactKeepsClassifierInIdentity() {
        LockPackage nativeLib = pkgWithJar(
                "io.netty", "netty-transport-native-epoll", "4.1.118.Final", DependencyScope.RUNTIME,
                "io/netty/netty-transport-native-epoll/4.1.118.Final/"
                        + "netty-transport-native-epoll-4.1.118.Final-linux-x86_64.jar");

        ResolvedModule module = mapper.fromLockPackages("com.example", "app", "1.0", List.of(nativeLib));

        ResolvedArtifact artifact = module.artifacts(VerifyScope.RUNTIME).get(0);
        assertEquals("linux-x86_64", artifact.classifier());
        assertEquals("io.netty:netty-transport-native-epoll:linux-x86_64", artifact.key());
    }

    @Test
    void emptyPackagesProduceEmptyModule() {
        ResolvedModule module = mapper.fromLockPackages("g", "a", "1.0", List.of());
        assertTrue(module.artifacts(VerifyScope.COMPILE).isEmpty());
        assertTrue(module.unmappedScopes().isEmpty());
    }

    private static LockPackage pkg(String group, String artifact, String version, DependencyScope scope) {
        return pkgWithJar(group, artifact, version, scope,
                group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar");
    }

    private static LockPackage pkgWithJar(
            String group, String artifact, String version, DependencyScope scope, String jarPath) {
        return new LockPackage(
                new PackageId(group, artifact),
                version,
                "central",
                scope,
                true,
                Optional.of(jarPath),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    private static List<String> coordinates(ResolvedModule module, VerifyScope scope) {
        return module.artifacts(scope).stream().map(ResolvedArtifact::coordinate).toList();
    }
}
