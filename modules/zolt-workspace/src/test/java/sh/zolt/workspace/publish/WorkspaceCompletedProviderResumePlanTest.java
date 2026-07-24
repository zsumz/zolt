package sh.zolt.workspace.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Full-family resume identity regressions for providers omitted from the emitted failed-tail command. */
final class WorkspaceCompletedProviderResumePlanTest {
    private static final WorkspacePublishService.Options OPTIONS =
            new WorkspacePublishService.Options(false, false, false, false, Optional.empty());

    @Test
    void completedProviderAddingASupplementalArtifactInvalidatesTheFullFamilyPlan() {
        StagedArtifact supplemental = new StagedArtifact(
                "com/acme/acme-core/1.0.0/acme-core-1.0.0-sources.jar",
                Path.of("target/acme-core-1.0.0-sources.jar"),
                "sources123");
        assertCompletedProviderPlanChangeRefused(
                provider("1.0.0", providerJar(), providerPom("def456")),
                provider("1.0.0", providerJar(), providerPom("def456"), supplemental));
    }

    @Test
    void completedProviderRemovingASupplementalArtifactInvalidatesTheFullFamilyPlan() {
        StagedArtifact supplemental = new StagedArtifact(
                "com/acme/acme-core/1.0.0/acme-core-1.0.0-sources.jar",
                Path.of("target/acme-core-1.0.0-sources.jar"),
                "sources123");
        assertCompletedProviderPlanChangeRefused(
                provider("1.0.0", providerJar(), providerPom("def456"), supplemental),
                provider("1.0.0", providerJar(), providerPom("def456")));
    }

    @Test
    void completedProviderVersionChangeInvalidatesTheFullFamilyPlan() {
        StagedArtifact versionTwoJar = new StagedArtifact(
                "com/acme/acme-core/2.0.0/acme-core-2.0.0.jar",
                Path.of("target/acme-core-2.0.0.jar"),
                "version2jar");
        StagedArtifact versionTwoPom = new StagedArtifact(
                "com/acme/acme-core/2.0.0/acme-core-2.0.0.pom",
                Path.of("target/acme-core-2.0.0.pom"),
                "version2pom");
        assertCompletedProviderPlanChangeRefused(
                provider("1.0.0", providerJar(), providerPom("def456")),
                provider("2.0.0", versionTwoJar, versionTwoPom));
    }

    @Test
    void completedProviderGeneratedPomChangeInvalidatesTheFullFamilyPlan() {
        assertCompletedProviderPlanChangeRefused(
                provider("1.0.0", providerJar(), providerPom("original-pom")),
                provider("1.0.0", providerJar(), providerPom("changed-pom")));
    }

    private static StagedArtifact providerJar() {
        return new StagedArtifact(
                "com/acme/acme-core/1.0.0/acme-core-1.0.0.jar",
                Path.of("target/acme-core-1.0.0.jar"),
                "abc123");
    }

    private static StagedArtifact providerPom(String hash) {
        return new StagedArtifact(
                "com/acme/acme-core/1.0.0/acme-core-1.0.0.pom",
                Path.of("target/acme-core-1.0.0.pom"),
                hash);
    }

    private static StagedMember provider(String version, StagedArtifact... artifacts) {
        return stagedMember(
                "acme-core",
                "com.acme:acme-core:" + version,
                artifacts);
    }

    private static void assertCompletedProviderPlanChangeRefused(
            StagedMember originalProvider, StagedMember changedProvider) {
        StagedMember consumer = stagedMember(
                "acme-http",
                "com.acme:acme-http:1.0.0",
                new StagedArtifact(
                        "com/acme/acme-http/1.0.0/acme-http-1.0.0.jar",
                        Path.of("target/acme-http-1.0.0.jar"),
                        "http123"));
        Set<String> completed = originalProvider.artifacts().stream()
                .map(StagedArtifact::repositoryPath)
                .collect(java.util.stream.Collectors.toSet());
        ResumeState manifest = ResumeState.of(
                List.of(originalProvider, consumer),
                List.of(consumer),
                OPTIONS,
                List.of("acme-http"),
                completed);

        List<String> blockers =
                manifest.validate(List.of(changedProvider, consumer), OPTIONS, List.of("acme-http"));

        assertEquals(1, blockers.size(), blockers::toString);
        assertTrue(blockers.getFirst().contains("publish plan changed"), blockers::toString);
    }

    private static StagedMember stagedMember(
            String path,
            String coordinate,
            StagedArtifact... artifacts) {
        RepositoryTarget target = RepositoryTarget.remote(
                URI.create("https://repo.example/releases"), Optional.empty());
        WorkspacePublishReport.Member reportMember =
                new WorkspacePublishReport.Member(path, coordinate, false, null);
        return new StagedMember(reportMember, target, "unsigned", List.of(artifacts));
    }
}
