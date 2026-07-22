package sh.zolt.explain.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class GradleDependencyTreeParserTest {
    private final GradleDependencyTreeParser parser = new GradleDependencyTreeParser();

    @Test
    void parsesSingleProjectBucketingScopesAndExcludingBom() {
        List<ResolvedModule> modules = parser.parse(
                GradleTreeFixtures.singleProject(),
                Map.of(":", new GradleProjectCoordinates("dev.zolt.examples", "gradle-simple", "1.0.0")));

        assertEquals(1, modules.size());
        ResolvedModule module = modules.get(0);
        assertEquals("dev.zolt.examples:gradle-simple", module.moduleKey());

        // compile = compileClasspath ∩ runtimeClasspath: Guava plus its five transitives, nothing else.
        assertEquals(
                List.of(
                        "com.google.errorprone:error_prone_annotations:2.36.0",
                        "com.google.guava:failureaccess:1.0.3",
                        "com.google.guava:guava:33.4.8-jre",
                        "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
                        "com.google.j2objc:j2objc-annotations:3.0.0",
                        "org.jspecify:jspecify:1.0.0"),
                coordinates(module, VerifyScope.COMPILE));
        // Non-cumulative buckets: the compile deps do NOT reappear as runtime or provided.
        assertTrue(module.artifacts(VerifyScope.RUNTIME).isEmpty());
        assertTrue(module.artifacts(VerifyScope.PROVIDED).isEmpty());

        // test = testRuntimeClasspath \ (compile ∪ runtime): only the JUnit jars (7); Guava does not
        // reappear, the junit-bom platform node and its (c) constraints are excluded, and (*) repeats
        // are deduplicated.
        List<String> test = keys(module, VerifyScope.TEST);
        assertEquals(7, test.size());
        assertFalse(test.contains("com.google.guava:guava"), "compile deps must not reappear on test: " + test);
        assertFalse(test.contains("org.junit:junit-bom"), "BOM/platform node must be excluded: " + test);
        assertTrue(test.contains("org.junit.platform:junit-platform-engine"), test.toString());
        assertTrue(test.contains("org.opentest4j:opentest4j"), test.toString());
        assertTrue(module.unmappedScopes().isEmpty());
    }

    @Test
    void mapsProjectDependenciesAndDerivesProvidedAndRuntimeScopes() {
        Map<String, GradleProjectCoordinates> projects = Map.of(
                ":", new GradleProjectCoordinates("com.example", "demo-mp", "2.1.0"),
                ":app", new GradleProjectCoordinates("com.example", "app", "2.1.0"),
                ":lib", new GradleProjectCoordinates("com.example", "lib", "2.1.0"));

        List<ResolvedModule> modules = parser.parse(GradleTreeFixtures.multiProject(), projects);
        assertEquals(3, modules.size());

        ResolvedModule app = moduleByKey(modules, "com.example:app");
        // `project :lib` (on compile and runtime) resolves to the sibling's coordinate on compile,
        // mirroring how Maven lists reactor deps.
        assertTrue(coordinates(app, VerifyScope.COMPILE).contains("com.example:lib:2.1.0"),
                coordinates(app, VerifyScope.COMPILE).toString());
        // Lombok is compileOnly (compileClasspath \ runtimeClasspath) -> provided, not compile.
        assertTrue(keys(app, VerifyScope.PROVIDED).contains("org.projectlombok:lombok"));
        assertFalse(keys(app, VerifyScope.COMPILE).contains("org.projectlombok:lombok"));
        // slf4j-api reaches app only through the sibling's runtime (runtimeClasspath \ compileClasspath).
        assertTrue(keys(app, VerifyScope.RUNTIME).contains("org.slf4j:slf4j-api"));
        // The BOM is excluded on the test classpath, and Guava (compile) does not reappear on test.
        assertFalse(keys(app, VerifyScope.TEST).contains("org.junit:junit-bom"));
        assertFalse(keys(app, VerifyScope.TEST).contains("com.google.guava:guava"));
        assertTrue(keys(app, VerifyScope.TEST).contains("org.junit.jupiter:junit-jupiter-api"));
        assertTrue(app.unmappedScopes().isEmpty());

        ResolvedModule lib = moduleByKey(modules, "com.example:lib");
        assertEquals(
                List.of("org.apache.commons:commons-lang3:3.17.0", "org.slf4j:slf4j-api:2.0.13"),
                coordinates(lib, VerifyScope.COMPILE));
    }

    @Test
    void takesResolvedRightHandSideOfConflictArrow() {
        List<ResolvedModule> modules = parser.parse(
                GradleTreeFixtures.conflict(),
                Map.of(":", new GradleProjectCoordinates("com.example", "conflict-demo", "1.0.0")));

        ResolvedModule module = modules.get(0);
        List<String> compile = coordinates(module, VerifyScope.COMPILE);
        assertEquals(
                List.of(
                        "ch.qos.logback:logback-classic:1.5.6",
                        "ch.qos.logback:logback-core:1.5.6",
                        "org.slf4j:slf4j-api:2.0.13"),
                compile);
    }

    @Test
    void unknownProjectDependencyIsDroppedRatherThanMisattributed() {
        // No mapping for :lib -> the project() edge cannot be resolved and is skipped, not guessed.
        List<ResolvedModule> modules = parser.parse(
                GradleTreeFixtures.multiProject(),
                Map.of(":app", new GradleProjectCoordinates("com.example", "app", "2.1.0")));
        ResolvedModule app = moduleByKey(modules, "com.example:app");
        assertFalse(keys(app, VerifyScope.COMPILE).contains("com.example:lib"));
        // Real external dependencies are still present.
        assertTrue(keys(app, VerifyScope.COMPILE).contains("com.google.guava:guava"));
    }

    @Test
    void toleratesBlankAndEmptyInput() {
        assertTrue(parser.parse("", Map.of()).isEmpty());
        assertTrue(parser.parse(null, Map.of()).isEmpty());
        assertTrue(parser.parse("   \n \n", Map.of()).isEmpty());
    }

    private static List<String> coordinates(ResolvedModule module, VerifyScope scope) {
        return module.artifacts(scope).stream().map(ResolvedArtifact::coordinate).collect(Collectors.toList());
    }

    private static List<String> keys(ResolvedModule module, VerifyScope scope) {
        return module.artifacts(scope).stream().map(ResolvedArtifact::key).collect(Collectors.toList());
    }

    private static ResolvedModule moduleByKey(List<ResolvedModule> modules, String key) {
        return modules.stream().filter(module -> module.moduleKey().equals(key)).findFirst().orElseThrow();
    }
}
