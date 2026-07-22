package sh.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.ConflictSelectionReason;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.resolve.graph.PackageNode;
import sh.zolt.resolve.graph.ResolutionGraph;
import sh.zolt.resolve.materialization.RepositoryOverlay;
import sh.zolt.resolve.metrics.ResolveMetrics;
import sh.zolt.resolve.progress.ArtifactProgressListener;
import sh.zolt.resolve.request.DependencyRequest;
import sh.zolt.resolve.request.RequestOrigin;
import sh.zolt.resolve.version.VersionConflict;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ResolverModelTest {
    @Test
    void dependencyRequestMakesDirectAndRequestedVersionExplicit() {
        DependencyRequest request = new DependencyRequest(
                new PackageId("com.google.guava", "guava"),
                "33.4.0-jre",
                DependencyScope.COMPILE,
                RequestOrigin.DIRECT);

        assertTrue(request.direct());
        assertEquals("33.4.0-jre", request.requestedVersion());
    }

    @Test
    void packageNodeMakesSelectedVersionExplicit() {
        PackageNode node = new PackageNode(new PackageId("com.google.guava", "guava"), "33.4.0-jre");

        assertEquals("33.4.0-jre", node.selectedVersion());
    }

    @Test
    void versionConflictSeparatesRequestsFromSelectedVersion() {
        PackageId packageId = new PackageId("org.slf4j", "slf4j-api");
        VersionConflict conflict = new VersionConflict(
                packageId,
                List.of(
                        new DependencyRequest(packageId, "2.0.15", DependencyScope.COMPILE, RequestOrigin.TRANSITIVE),
                        new DependencyRequest(packageId, "2.0.16", DependencyScope.COMPILE, RequestOrigin.TRANSITIVE)),
                "2.0.16",
                ConflictSelectionReason.NEWEST_VERSION);

        assertEquals(List.of("2.0.15", "2.0.16"), conflict.requests().stream().map(DependencyRequest::requestedVersion).toList());
        assertEquals("2.0.16", conflict.selectedVersion());
        assertEquals(ConflictSelectionReason.NEWEST_VERSION, conflict.selectionReason());
    }

    @Test
    void graphCollectionsAreImmutable() {
        List<PackageNode> nodes = new ArrayList<>();
        nodes.add(new PackageNode(new PackageId("com.example", "demo"), "1.0.0"));
        ResolutionGraph graph = new ResolutionGraph(nodes, List.of(), List.of());
        nodes.clear();

        assertEquals(1, graph.nodes().size());
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().add(
                new PackageNode(new PackageId("com.example", "other"), "1.0.0")));
    }

    @Test
    void resolveOptionsNormalizeDefaultsAndCopyOverlayLists() {
        List<RepositoryOverlay> overlays = new ArrayList<>();
        overlays.add(RepositoryOverlay.mavenLocal(Path.of("repo")));
        ArtifactProgressListener listener = new ArtifactProgressListener() {
        };

        ResolveOptions options = new ResolveOptions(
                true,
                overlays,
                false,
                false,
                "  zolt resolve --workspace  ",
                listener,
                java.util.Set.of(new sh.zolt.dependency.PackageId("com.example", "member")));
        overlays.clear();

        assertTrue(options.offline());
        assertEquals("zolt resolve --workspace", options.retryCommand());
        assertEquals(listener, options.artifactProgressListener());
        assertEquals(1, options.repositoryOverlays().size());
        assertEquals("local-overlay:maven-local", options.repositoryOverlays().getFirst().lockfileSource());
        assertThrows(UnsupportedOperationException.class, () -> options.repositoryOverlays().clear());
        assertEquals(
                java.util.Set.of(new sh.zolt.dependency.PackageId("com.example", "member")),
                options.workspaceMemberCoordinates());
        assertThrows(UnsupportedOperationException.class, () -> options.workspaceMemberCoordinates().clear());

        ResolveOptions defaults = new ResolveOptions(false, null, false, false, " ", null, null);
        assertEquals("zolt resolve", defaults.retryCommand());
        assertEquals(List.of(), defaults.repositoryOverlays());
        assertEquals(java.util.Set.of(), defaults.workspaceMemberCoordinates());
        assertEquals(ArtifactProgressListener.NOOP, defaults.artifactProgressListener());
        assertTrue(defaults.withCoverageTooling().includeCoverageTooling());
        assertEquals("zolt resolve --locked", defaults.withRetryCommand("zolt resolve --locked").retryCommand());
    }

    @Test
    void resolveOptionsRejectLocalOverlayAndRejectionCombination() {
        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> new ResolveOptions(
                        false,
                        List.of(RepositoryOverlay.mavenLocal(Path.of("repo"))),
                        true,
                        false));

        assertTrue(exception.getMessage().contains("Cannot combine local repository overlays"));
        assertTrue(exception.getMessage().contains("Remove --repository-overlay or remove --no-local-overlays"));
    }

    @Test
    void resolveResultAndOutputDefaultNullMetricsToEmptyMetrics() {
        Path lockfile = Path.of("zolt.lock");
        ResolveMetrics metrics = ResolveMetrics.empty().withLockfileWriteNanos(42L);
        ZoltLockfile model = new ZoltLockfile(ZoltLockfile.CURRENT_VERSION, List.of(), List.of());

        ResolveResult defaultedResult = new ResolveResult(2, 1, 0, lockfile);
        ResolveResult nullMetricsResult = new ResolveResult(2, 1, 0, lockfile, null);
        ResolveResult explicitMetricsResult = new ResolveResult(2, 1, 0, lockfile, metrics);
        ResolveOutput defaultedOutput = new ResolveOutput(model, 1);
        ResolveOutput nullMetricsOutput = new ResolveOutput(model, 1, null);
        ResolveOutput explicitMetricsOutput = new ResolveOutput(model, 1, metrics);

        assertEquals(ResolveMetrics.empty(), defaultedResult.metrics());
        assertEquals(ResolveMetrics.empty(), nullMetricsResult.metrics());
        assertEquals(42L, explicitMetricsResult.metrics().lockfileWriteNanos());
        assertEquals(ResolveMetrics.empty(), defaultedOutput.metrics());
        assertEquals(ResolveMetrics.empty(), nullMetricsOutput.metrics());
        assertEquals(42L, explicitMetricsOutput.metrics().lockfileWriteNanos());
        assertEquals(model, explicitMetricsOutput.lockfile());
    }
}
