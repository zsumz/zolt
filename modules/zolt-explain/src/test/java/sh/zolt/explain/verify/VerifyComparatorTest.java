package sh.zolt.explain.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class VerifyComparatorTest {
    private final VerifyComparator comparator = new VerifyComparator();

    @Test
    void classifiesMatchedDriftAndOneSidedArtifacts() {
        ResolvedModule maven = module("com.example", "app", Map.of(VerifyScope.COMPILE, List.of(
                artifact("org.a", "match", "1.0.0"),
                artifact("org.b", "drift", "2.0.0"),
                artifact("org.c", "maven-only", "3.0.0"))));
        ResolvedModule zolt = module("com.example", "app", Map.of(VerifyScope.COMPILE, List.of(
                artifact("org.a", "match", "1.0.0"),
                artifact("org.b", "drift", "2.1.0"),
                artifact("org.d", "zolt-only", "4.0.0"))));

        VerifyReport report = comparator.compare(
                "/maven", "/zolt", List.of(maven), List.of(zolt), Map.of("com.example:app", "."), Map.of("com.example:app", "."));

        assertEquals(1, report.modules().size());
        ModuleComparison module = report.modules().get(0);
        assertEquals(ModulePresence.BOTH, module.presence());
        assertEquals(Optional.of("."), module.mavenDirectory());
        ScopeComparison compile = scope(module, VerifyScope.COMPILE);
        assertEquals(List.of("org.a:match"), compile.matched().stream().map(ResolvedArtifact::key).toList());
        assertEquals(1, compile.versionDrift().size());
        assertEquals("org.b:drift", compile.versionDrift().get(0).key());
        assertEquals("2.0.0", compile.versionDrift().get(0).mavenVersion());
        assertEquals("2.1.0", compile.versionDrift().get(0).zoltVersion());
        assertEquals(List.of("org.c:maven-only"), compile.onlyInMaven().stream().map(ResolvedArtifact::key).toList());
        assertEquals(List.of("org.d:zolt-only"), compile.onlyInZolt().stream().map(ResolvedArtifact::key).toList());

        VerifySummary summary = report.summary();
        assertEquals(1, summary.matched());
        assertEquals(1, summary.versionDrift());
        assertEquals(1, summary.onlyInMaven());
        assertEquals(1, summary.onlyInZolt());
        assertTrue(report.hasDifferences());
    }

    @Test
    void identicalResolutionReportsNoDifferences() {
        ResolvedModule maven = module("g", "a", Map.of(
                VerifyScope.COMPILE, List.of(artifact("org.x", "lib", "1.0.0")),
                VerifyScope.TEST, List.of(artifact("org.junit.jupiter", "junit-jupiter", "5.11.4"))));
        ResolvedModule zolt = module("g", "a", Map.of(
                VerifyScope.COMPILE, List.of(artifact("org.x", "lib", "1.0.0")),
                VerifyScope.TEST, List.of(artifact("org.junit.jupiter", "junit-jupiter", "5.11.4"))));

        VerifyReport report = comparator.compare("/m", "/z", List.of(maven), List.of(zolt), Map.of(), Map.of());

        assertFalse(report.hasDifferences());
        assertEquals(2, report.summary().matched());
        assertEquals(0, report.summary().versionDrift());
        assertEquals(1, report.summary().modulesBoth());
    }

    @Test
    void reportsModulesPresentOnOnlyOneSide() {
        ResolvedModule mavenOnly = module("g", "mvn-only", Map.of(
                VerifyScope.COMPILE, List.of(artifact("org.x", "lib", "1.0.0"))));
        ResolvedModule zoltOnly = module("g", "zolt-only", Map.of(
                VerifyScope.RUNTIME, List.of(artifact("org.y", "lib", "2.0.0"))));

        VerifyReport report = comparator.compare(
                "/m", "/z", List.of(mavenOnly), List.of(zoltOnly), Map.of(), Map.of());

        assertEquals(2, report.modules().size());
        assertEquals(ModulePresence.MAVEN_ONLY, moduleByKey(report, "g:mvn-only").presence());
        assertEquals(ModulePresence.ZOLT_ONLY, moduleByKey(report, "g:zolt-only").presence());
        // A maven-only module's artifacts land in only-in-maven; a zolt-only module's in only-in-zolt.
        assertEquals(1, report.summary().onlyInMaven());
        assertEquals(1, report.summary().onlyInZolt());
        assertEquals(1, report.summary().modulesMavenOnly());
        assertEquals(1, report.summary().modulesZoltOnly());
        assertTrue(report.hasDifferences());
    }

    @Test
    void carriesUnmappedScopeNotes() {
        ResolvedModule maven = new ResolvedModule("g", "a", "1.0", "jar",
                Map.of(VerifyScope.COMPILE, List.of(artifact("org.x", "lib", "1.0.0"))),
                Map.of("system", 1));
        ResolvedModule zolt = new ResolvedModule("g", "a", "1.0", "jar",
                Map.of(VerifyScope.COMPILE, List.of(artifact("org.x", "lib", "1.0.0"))),
                Map.of("dev", 2, "processor", 1));

        VerifyReport report = comparator.compare("/m", "/z", List.of(maven), List.of(zolt), Map.of(), Map.of());

        List<String> notes = report.modules().get(0).notes();
        assertEquals(List.of(
                "Maven scopes not compared: system (1)",
                "Zolt scopes not compared: dev (2), processor (1)"), notes);
        // Out-of-scope buckets are informational only — they do not make the report differ.
        assertFalse(report.hasDifferences());
    }

    private static ResolvedArtifact artifact(String group, String artifact, String version) {
        return new ResolvedArtifact(group, artifact, "jar", "", version);
    }

    private static ResolvedModule module(
            String group, String artifact, Map<VerifyScope, List<ResolvedArtifact>> scopes) {
        return new ResolvedModule(group, artifact, "1.0.0", "jar", scopes, Map.of());
    }

    private static ScopeComparison scope(ModuleComparison module, VerifyScope scope) {
        return module.scopes().stream()
                .filter(comparison -> comparison.scope() == scope)
                .findFirst()
                .orElseThrow();
    }

    private static ModuleComparison moduleByKey(VerifyReport report, String key) {
        return report.modules().stream()
                .filter(module -> module.moduleKey().equals(key))
                .findFirst()
                .orElseThrow();
    }
}
