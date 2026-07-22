package sh.zolt.explain.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class MavenDependencyTreeParserTest {
    private final MavenDependencyTreeParser parser = new MavenDependencyTreeParser();

    @Test
    void parsesSingleModuleAndBucketsByScope() {
        List<ResolvedModule> modules = parser.parse(MavenTreeFixtures.singleModule());

        assertEquals(1, modules.size());
        ResolvedModule module = modules.get(0);
        assertEquals("dev.zolt.examples:maven-simple", module.moduleKey());
        assertEquals("1.0.0", module.version());

        // The Guava subtree flattens to the resolved compile set (Guava + six transitives).
        assertEquals(
                List.of(
                        "com.google.code.findbugs:jsr305:3.0.2",
                        "com.google.errorprone:error_prone_annotations:2.36.0",
                        "com.google.guava:failureaccess:1.0.2",
                        "com.google.guava:guava:33.4.8-jre",
                        "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
                        "com.google.j2objc:j2objc-annotations:3.0.0",
                        "org.checkerframework:checker-qual:3.43.0"),
                coordinates(module, VerifyScope.COMPILE));
        assertTrue(coordinates(module, VerifyScope.TEST).contains("org.junit.jupiter:junit-jupiter:5.11.4"));
        assertEquals(8, module.artifacts(VerifyScope.TEST).size());
        assertTrue(module.artifacts(VerifyScope.RUNTIME).isEmpty());
        assertTrue(module.unmappedScopes().isEmpty());
    }

    @Test
    void parsesMultiModuleReactorWithClassifierRuntimeAndProvided() {
        List<ResolvedModule> modules = parser.parse(MavenTreeFixtures.multiModule());

        assertEquals(2, modules.size());
        ResolvedModule app = modules.stream()
                .filter(module -> module.moduleKey().equals("com.example:app"))
                .findFirst()
                .orElseThrow();
        ResolvedModule core = modules.stream()
                .filter(module -> module.moduleKey().equals("com.example:core"))
                .findFirst()
                .orElseThrow();

        assertEquals(
                List.of("com.example:core:2.1.0", "org.slf4j:slf4j-api:2.0.13"),
                coordinates(app, VerifyScope.COMPILE));

        // The runtime classifier artifact keeps its classifier in the coordinate and in the key.
        ResolvedArtifact netty = app.artifacts(VerifyScope.RUNTIME).get(0);
        assertEquals("linux-x86_64", netty.classifier());
        assertEquals("io.netty:netty-transport-native-epoll:linux-x86_64", netty.key());
        assertEquals(
                "io.netty:netty-transport-native-epoll:linux-x86_64:4.1.118.Final", netty.coordinate());

        assertEquals(
                List.of("jakarta.servlet:jakarta.servlet-api:6.0.0"),
                coordinates(app, VerifyScope.PROVIDED));
        assertEquals(2, app.artifacts(VerifyScope.TEST).size());
        assertEquals(
                List.of("com.google.guava:guava:33.4.8-jre", "org.slf4j:slf4j-api:2.0.13"),
                coordinates(core, VerifyScope.COMPILE));
    }

    @Test
    void stripsAnnotationsAndRecordsUnmappedScopes() {
        List<ResolvedModule> modules = parser.parse(MavenTreeFixtures.edgeCases());

        assertEquals(1, modules.size());
        ResolvedModule module = modules.get(0);
        // The `(optional)` annotation is stripped, leaving a clean compile coordinate.
        assertEquals(List.of("com.foo:bar:1.2.3"), coordinates(module, VerifyScope.COMPILE));
        assertEquals(List.of("org.test:only:2.0.0"), coordinates(module, VerifyScope.TEST));
        // The system-scoped artifact is surfaced as a note, not dropped and not compared.
        assertEquals(1, module.unmappedScopes().getOrDefault("system", 0));
    }

    @Test
    void toleratesBlankAndEmptyInput() {
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   \n  \n").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }

    private static List<String> coordinates(ResolvedModule module, VerifyScope scope) {
        return module.artifacts(scope).stream()
                .map(ResolvedArtifact::coordinate)
                .collect(Collectors.toList());
    }
}
