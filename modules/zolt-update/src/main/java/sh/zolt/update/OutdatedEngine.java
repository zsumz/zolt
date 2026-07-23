package sh.zolt.update;

import sh.zolt.dependency.VersionCandidates;
import sh.zolt.dependency.VersionClassifier;
import sh.zolt.maven.metadata.MetadataDiscovery;
import sh.zolt.maven.metadata.VersionDiscovery;
import sh.zolt.maven.repository.RepositoryAccess;
import sh.zolt.maven.repository.RepositoryAccessPlanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Computes an outdated report for one or more scopes: enumerates every version surface, discovers
 * available versions (advisory-only), classifies update targets, applies selector and status
 * filters, orders deterministically (aliases first, then group:artifact), and — across a workspace —
 * annotates coordinates shared by multiple members.
 */
public final class OutdatedEngine {
    private static final Comparator<OutdatedEntry> ENTRY_ORDER = Comparator
            .comparingInt((OutdatedEntry entry) -> entry.surface() == OutdatedSurface.VERSION_ALIAS ? 0 : 1)
            .thenComparing(OutdatedEntry::identifier)
            .thenComparing(OutdatedEntry::section);

    private final VersionDiscovery discovery;
    private final RepositoryAccessPlanner planner;
    private final SurfaceCollector collector = new SurfaceCollector();
    private final VersionClassifier classifier = new VersionClassifier();

    public OutdatedEngine(VersionDiscovery discovery) {
        this(discovery, new RepositoryAccessPlanner());
    }

    public OutdatedEngine(VersionDiscovery discovery, RepositoryAccessPlanner planner) {
        this.discovery = discovery;
        this.planner = planner;
    }

    public OutdatedReport report(List<OutdatedScope> scopes, OutdatedOptions options) {
        List<OutdatedScopeReport> scopeReports = new ArrayList<>();
        for (OutdatedScope scope : scopes) {
            scopeReports.add(reportScope(scope, options));
        }
        return applyWorkspaceDedup(scopeReports);
    }

    private OutdatedScopeReport reportScope(OutdatedScope scope, OutdatedOptions options) {
        List<RepositoryAccess> repositories = planner.plan(scope.config());
        Map<String, MetadataDiscovery> memo = new LinkedHashMap<>();
        List<OutdatedEntry> entries = new ArrayList<>();
        for (SurfaceRequest surface : collector.collect(scope.config())) {
            OutdatedEntry entry = evaluate(surface, repositories, options, memo);
            if (include(entry, options)) {
                entries.add(entry);
            }
        }
        entries.sort(ENTRY_ORDER);
        return new OutdatedScopeReport(scope.label(), entries);
    }

    private OutdatedEntry evaluate(
            SurfaceRequest surface,
            List<RepositoryAccess> repositories,
            OutdatedOptions options,
            Map<String, MetadataDiscovery> memo) {
        MetadataDiscovery discovered = discover(surface, repositories, options.offline(), memo);
        if (!discovered.resolved()) {
            return entry(surface, OutdatedStatus.UNKNOWN, OutdatedCandidates.none(), Optional.empty(), discovered.notes());
        }
        VersionCandidates candidates =
                classifier.candidates(surface.currentVersion(), discovered.versions(), options.includePrereleases());
        OutdatedStatus status = candidates.updateAvailable() ? OutdatedStatus.UPDATE_AVAILABLE : OutdatedStatus.CURRENT;
        Optional<String> source = candidates.selectedLatest().flatMap(discovered::source);
        return entry(surface, status, toCandidates(surface.currentVersion(), candidates), source, discovered.notes());
    }

    private MetadataDiscovery discover(
            SurfaceRequest surface,
            List<RepositoryAccess> repositories,
            boolean offline,
            Map<String, MetadataDiscovery> memo) {
        if (surface.queryCoordinates().isEmpty()) {
            return new MetadataDiscovery(false, List.of(), Map.of(), List.of());
        }
        if (!surface.intersect()) {
            return discoverCoordinate(surface.queryCoordinates().get(0), repositories, offline, memo);
        }
        return intersect(surface.queryCoordinates(), repositories, offline, memo);
    }

    private MetadataDiscovery intersect(
            List<DiscoveryCoordinate> coordinates,
            List<RepositoryAccess> repositories,
            boolean offline,
            Map<String, MetadataDiscovery> memo) {
        List<String> intersection = null;
        Map<String, String> sourceByVersion = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        boolean allResolved = true;
        for (DiscoveryCoordinate coordinate : coordinates) {
            MetadataDiscovery discovered = discoverCoordinate(coordinate, repositories, offline, memo);
            notes.addAll(discovered.notes());
            if (!discovered.resolved()) {
                allResolved = false;
                continue;
            }
            intersection = intersection == null
                    ? new ArrayList<>(discovered.versions())
                    : retain(intersection, discovered.versions());
            discovered.sourceByVersion().forEach(sourceByVersion::putIfAbsent);
        }
        List<String> versions = intersection == null ? List.of() : List.copyOf(intersection);
        return new MetadataDiscovery(allResolved && intersection != null, versions, sourceByVersion, notes);
    }

    private static List<String> retain(List<String> current, List<String> candidate) {
        List<String> retained = new ArrayList<>(current);
        retained.retainAll(candidate);
        return retained;
    }

    private MetadataDiscovery discoverCoordinate(
            DiscoveryCoordinate coordinate,
            List<RepositoryAccess> repositories,
            boolean offline,
            Map<String, MetadataDiscovery> memo) {
        return memo.computeIfAbsent(
                coordinate.coordinate(),
                ignored -> discovery.discover(repositories, coordinate.groupId(), coordinate.artifactId(), offline));
    }

    private OutdatedCandidates toCandidates(String current, VersionCandidates candidates) {
        return new OutdatedCandidates(
                candidates.latestPatch(),
                candidates.latestMinor(),
                candidates.latestMajor(),
                candidates.selectedInMajor(),
                candidates.selectedInMajor().map(version -> classifier.classify(current, version)),
                candidates.selectedLatest(),
                candidates.selectedLatest().map(version -> classifier.classify(current, version)));
    }

    private static OutdatedEntry entry(
            SurfaceRequest surface,
            OutdatedStatus status,
            OutdatedCandidates candidates,
            Optional<String> source,
            List<String> notes) {
        return new OutdatedEntry(
                surface.surface(),
                surface.identifier(),
                surface.section(),
                surface.currentVersion(),
                status,
                candidates,
                source,
                surface.governs(),
                List.of(),
                notes);
    }

    private static boolean include(OutdatedEntry entry, OutdatedOptions options) {
        if (!options.includeUpToDate() && entry.status() == OutdatedStatus.CURRENT) {
            return false;
        }
        return options.selectors().isEmpty()
                || options.selectors().stream().anyMatch(selector -> matchesSelector(entry, selector));
    }

    private static boolean matchesSelector(OutdatedEntry entry, String selector) {
        String sectionToken = entry.section().replace("[", "").replace("]", "");
        return entry.identifier().equals(selector)
                || entry.surface().jsonName().equals(selector)
                || sectionToken.equals(selector)
                || sectionToken.startsWith(selector + ".");
    }

    private OutdatedReport applyWorkspaceDedup(List<OutdatedScopeReport> scopeReports) {
        if (scopeReports.size() <= 1) {
            return new OutdatedReport(scopeReports, List.of());
        }
        Map<String, List<String>> membersByIdentifier = new TreeMap<>();
        for (OutdatedScopeReport scope : scopeReports) {
            for (OutdatedEntry entry : scope.entries()) {
                membersByIdentifier
                        .computeIfAbsent(entry.identifier(), ignored -> new ArrayList<>())
                        .add(scope.label());
            }
        }
        List<OutdatedScopeReport> annotated = new ArrayList<>();
        for (OutdatedScopeReport scope : scopeReports) {
            List<OutdatedEntry> entries = new ArrayList<>();
            for (OutdatedEntry entry : scope.entries()) {
                List<String> members = membersByIdentifier.get(entry.identifier());
                entries.add(members.size() > 1 ? entry.withMembers(List.copyOf(members)) : entry);
            }
            annotated.add(new OutdatedScopeReport(scope.label(), entries));
        }
        return new OutdatedReport(annotated, sharedNotes(membersByIdentifier));
    }

    private static List<String> sharedNotes(Map<String, List<String>> membersByIdentifier) {
        List<String> notes = new ArrayList<>();
        membersByIdentifier.forEach((identifier, members) -> {
            if (members.size() > 1) {
                notes.add(identifier + " is shared by members " + String.join(", ", members) + ".");
            }
        });
        return notes;
    }
}
