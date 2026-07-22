package sh.zolt.explain.verify;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compares Maven-resolved and Zolt-resolved dependency sets, per module and per scope, into a
 * deterministic {@link VerifyReport}. Modules are joined by {@code group:artifact}; artifacts are
 * joined by {@code group:artifact[:classifier]} within a scope. The comparator states facts only —
 * matched, version drift (both versions reported), only-in-Maven, only-in-Zolt — and never judges
 * whether a difference is acceptable.
 */
public final class VerifyComparator {

    private static final Comparator<VersionDrift> DRIFT_ORDER = Comparator
            .comparing(VersionDrift::groupId)
            .thenComparing(VersionDrift::artifactId)
            .thenComparing(VersionDrift::classifier);

    public VerifyReport compare(
            String mavenRoot,
            String zoltRoot,
            List<ResolvedModule> mavenModules,
            List<ResolvedModule> zoltModules,
            Map<String, String> mavenDirectories,
            Map<String, String> zoltMembers) {
        Map<String, ResolvedModule> maven = index(mavenModules);
        Map<String, ResolvedModule> zolt = index(zoltModules);
        Map<String, String> mavenDirs = mavenDirectories == null ? Map.of() : mavenDirectories;
        Map<String, String> members = zoltMembers == null ? Map.of() : zoltMembers;

        TreeSet<String> moduleKeys = new TreeSet<>();
        moduleKeys.addAll(maven.keySet());
        moduleKeys.addAll(zolt.keySet());

        List<ModuleComparison> comparisons = new ArrayList<>();
        SummaryAccumulator totals = new SummaryAccumulator();
        for (String moduleKey : moduleKeys) {
            ResolvedModule mavenModule = maven.get(moduleKey);
            ResolvedModule zoltModule = zolt.get(moduleKey);
            ModulePresence presence = presenceOf(mavenModule, zoltModule);
            totals.recordModule(presence);
            List<ScopeComparison> scopes = new ArrayList<>();
            for (VerifyScope scope : VerifyScope.values()) {
                ScopeComparison comparison = compareScope(scope, mavenModule, zoltModule);
                totals.recordScope(comparison);
                scopes.add(comparison);
            }
            comparisons.add(new ModuleComparison(
                    moduleKey,
                    presence,
                    Optional.ofNullable(mavenDirs.get(moduleKey)),
                    Optional.ofNullable(members.get(moduleKey)),
                    scopes,
                    notes(mavenModule, zoltModule)));
        }
        return new VerifyReport(mavenRoot, zoltRoot, comparisons, totals.summary(moduleKeys.size()));
    }

    private static ScopeComparison compareScope(
            VerifyScope scope, ResolvedModule mavenModule, ResolvedModule zoltModule) {
        Map<String, ResolvedArtifact> maven = byKey(mavenModule, scope);
        Map<String, ResolvedArtifact> zolt = byKey(zoltModule, scope);

        List<ResolvedArtifact> matched = new ArrayList<>();
        List<VersionDrift> drift = new ArrayList<>();
        List<ResolvedArtifact> onlyInMaven = new ArrayList<>();
        List<ResolvedArtifact> onlyInZolt = new ArrayList<>();

        for (Map.Entry<String, ResolvedArtifact> entry : maven.entrySet()) {
            ResolvedArtifact mavenArtifact = entry.getValue();
            ResolvedArtifact zoltArtifact = zolt.get(entry.getKey());
            if (zoltArtifact == null) {
                onlyInMaven.add(mavenArtifact);
            } else if (mavenArtifact.version().equals(zoltArtifact.version())) {
                matched.add(mavenArtifact);
            } else {
                drift.add(new VersionDrift(
                        mavenArtifact.groupId(),
                        mavenArtifact.artifactId(),
                        mavenArtifact.classifier(),
                        mavenArtifact.version(),
                        zoltArtifact.version()));
            }
        }
        for (Map.Entry<String, ResolvedArtifact> entry : zolt.entrySet()) {
            if (!maven.containsKey(entry.getKey())) {
                onlyInZolt.add(entry.getValue());
            }
        }
        matched.sort(ResolvedArtifact.ORDER);
        drift.sort(DRIFT_ORDER);
        onlyInMaven.sort(ResolvedArtifact.ORDER);
        onlyInZolt.sort(ResolvedArtifact.ORDER);
        return new ScopeComparison(scope, matched, drift, onlyInMaven, onlyInZolt);
    }

    private static Map<String, ResolvedArtifact> byKey(ResolvedModule module, VerifyScope scope) {
        Map<String, ResolvedArtifact> byKey = new TreeMap<>();
        if (module == null) {
            return byKey;
        }
        for (ResolvedArtifact artifact : module.artifacts(scope)) {
            byKey.put(artifact.key(), artifact);
        }
        return byKey;
    }

    private static ModulePresence presenceOf(ResolvedModule mavenModule, ResolvedModule zoltModule) {
        if (mavenModule != null && zoltModule != null) {
            return ModulePresence.BOTH;
        }
        return mavenModule != null ? ModulePresence.MAVEN_ONLY : ModulePresence.ZOLT_ONLY;
    }

    private static List<String> notes(ResolvedModule mavenModule, ResolvedModule zoltModule) {
        List<String> notes = new ArrayList<>();
        if (mavenModule != null && !mavenModule.unmappedScopes().isEmpty()) {
            notes.add("Maven scopes not compared: " + renderScopeCounts(mavenModule.unmappedScopes()));
        }
        if (zoltModule != null && !zoltModule.unmappedScopes().isEmpty()) {
            notes.add("Zolt scopes not compared: " + renderScopeCounts(zoltModule.unmappedScopes()));
        }
        return notes;
    }

    private static String renderScopeCounts(Map<String, Integer> counts) {
        List<String> rendered = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            rendered.add(entry.getKey() + " (" + entry.getValue() + ")");
        }
        return String.join(", ", rendered);
    }

    private static Map<String, ResolvedModule> index(List<ResolvedModule> modules) {
        Map<String, ResolvedModule> index = new LinkedHashMap<>();
        if (modules != null) {
            for (ResolvedModule module : modules) {
                index.putIfAbsent(module.moduleKey(), module);
            }
        }
        return index;
    }

    private static final class SummaryAccumulator {
        private int modulesBoth;
        private int modulesMavenOnly;
        private int modulesZoltOnly;
        private int matched;
        private int versionDrift;
        private int onlyInMaven;
        private int onlyInZolt;

        void recordModule(ModulePresence presence) {
            switch (presence) {
                case BOTH -> modulesBoth++;
                case MAVEN_ONLY -> modulesMavenOnly++;
                case ZOLT_ONLY -> modulesZoltOnly++;
            }
        }

        void recordScope(ScopeComparison comparison) {
            matched += comparison.matched().size();
            versionDrift += comparison.versionDrift().size();
            onlyInMaven += comparison.onlyInMaven().size();
            onlyInZolt += comparison.onlyInZolt().size();
        }

        VerifySummary summary(int modules) {
            return new VerifySummary(
                    modules,
                    modulesBoth,
                    modulesMavenOnly,
                    modulesZoltOnly,
                    matched,
                    versionDrift,
                    onlyInMaven,
                    onlyInZolt);
        }
    }
}
