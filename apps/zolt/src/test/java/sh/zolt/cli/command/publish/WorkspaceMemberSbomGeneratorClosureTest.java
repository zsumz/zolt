package sh.zolt.cli.command.publish;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.NativeSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.sbom.LockSbomAssembler;
import sh.zolt.sbom.SbomComponent;
import sh.zolt.sbom.SbomDependency;
import sh.zolt.sbom.SbomModel;
import sh.zolt.sbom.SbomScopeSelection;
import sh.zolt.workspace.publish.WorkspaceMemberSbomLockProjection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The per-member publish SBOM is materially complete. Driving the real production seam
 * ({@link WorkspaceMemberSbomLockProjection} feeding {@link PublishSbomArtifactGenerator#memberGenerator})
 * over an aggregated lock produces a CycloneDX that carries the member's full transitive closure,
 * artifact SHA-256 hashes, and the {@code root -> direct}, {@code direct -> transitive}, and
 * {@code root -> sibling} edges — the supply-chain evidence the POM-shaped projection silently drops.
 * Scope filtering still holds: a test-scope dependency the member declares never reaches the SBOM.
 */
final class WorkspaceMemberSbomGeneratorClosureTest {
    private static final String TOOL_VERSION = "0.1.0-TEST";
    private static final String SHA_DATABIND =
            "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String SHA_CORE =
            "2222222222222222222222222222222222222222222222222222222222222222";
    private static final String SHA_JUNIT =
            "4444444444444444444444444444444444444444444444444444444444444444";

    @TempDir
    private Path memberDir;

    @Test
    void producesCompleteMemberCycloneDxThroughTheRealGeneratorSeam() throws IOException {
        // acme-http: one direct external (jackson-databind -> jackson-core), one workspace sibling
        // (acme-core), and a test-only dependency that must not surface in the published SBOM.
        ProjectConfig config = ProjectConfigs.withWorkspaceDependencySections(
                new ProjectMetadata("acme-http", "1.0.0", "com.acme", "21", Optional.empty()),
                Map.of("central", ProjectConfig.MAVEN_CENTRAL),
                Map.of(),
                Map.of("com.fasterxml.jackson.core:jackson-databind", "2.19.2"),
                Set.of(),
                Map.of("com.acme:acme-core", "acme-core"),
                Map.of("org.junit.jupiter:junit-jupiter", "5.11.4"),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                NativeSettings.defaults());

        ZoltLockfile aggregated = new ZoltLockfile(
                1,
                List.of(
                        workspacePackage("com.acme", "acme-core", "1.0.0"),
                        external("com.fasterxml.jackson.core", "jackson-databind", "2.19.2",
                                DependencyScope.COMPILE, SHA_DATABIND,
                                List.of("com.fasterxml.jackson.core:jackson-core:2.19.2")),
                        external("com.fasterxml.jackson.core", "jackson-core", "2.19.2",
                                DependencyScope.COMPILE, SHA_CORE, List.of()),
                        external("org.junit.jupiter", "junit-jupiter", "5.11.4",
                                DependencyScope.TEST, SHA_JUNIT, List.of())),
                List.of());

        ZoltLockfile sbomLock = new WorkspaceMemberSbomLockProjection().project(config, aggregated);

        // The exact seam publish --workspace --sbom uses: project -> memberGenerator -> written file.
        Path sbomFile = new PublishSbomArtifactGenerator()
                .memberGenerator(true, TOOL_VERSION)
                .generate(memberDir, config, sbomLock)
                .orElseThrow();
        String json = Files.readString(sbomFile);

        // Produced file is a CycloneDX carrying BOTH externals and the sibling as components.
        assertTrue(json.contains("\"bomFormat\": \"CycloneDX\""), json);
        assertTrue(json.contains("jackson-databind"), json);
        assertTrue(json.contains("jackson-core"), json);
        assertTrue(json.contains("acme-core"), json);
        // Artifact SHA-256 hashes are present for both externals (the POM projection dropped these).
        assertTrue(json.contains("\"SHA-256\""), json);
        assertTrue(json.contains(SHA_DATABIND), json);
        assertTrue(json.contains(SHA_CORE), json);
        // Scope filtering: the test-only dependency and its hash never reach the published SBOM.
        assertFalse(json.contains("junit"), json);
        assertFalse(json.contains(SHA_JUNIT), json);

        // Edges: assert precisely on the model the writer serializes. memberGenerator assembles with
        // requiredOnly(), so this is the same graph the file above encodes.
        SbomModel model = new LockSbomAssembler().assemble(
                config, sbomLock, SbomScopeSelection.requiredOnly(), Optional.empty(), TOOL_VERSION);
        String rootRef = model.metadataComponent().bomRef();
        String databindRef = refByName(model, "jackson-databind");
        String coreRef = refByName(model, "jackson-core");
        String siblingRef = refByName(model, "acme-core");

        assertTrue(dependsOn(model, rootRef).contains(databindRef), "root -> direct external");
        assertTrue(dependsOn(model, rootRef).contains(siblingRef), "root -> workspace sibling");
        assertTrue(dependsOn(model, databindRef).contains(coreRef), "direct external -> transitive external");
        // Those bom-refs are exactly the ones serialized into the produced file.
        assertTrue(json.contains(databindRef), databindRef);
        assertTrue(json.contains(coreRef), coreRef);
        assertTrue(json.contains(siblingRef), siblingRef);
    }

    private static String refByName(SbomModel model, String name) {
        return model.components().stream()
                .filter(component -> component.name().equals(name))
                .map(SbomComponent::bomRef)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no component named " + name));
    }

    private static List<String> dependsOn(SbomModel model, String ref) {
        return model.dependencies().stream()
                .filter(dependency -> dependency.ref().equals(ref))
                .map(SbomDependency::dependsOn)
                .findFirst()
                .orElse(List.of());
    }

    private static LockPackage external(
            String group,
            String artifact,
            String version,
            DependencyScope scope,
            String jarSha256,
            List<String> dependencies) {
        String base = group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version;
        return new LockPackage(
                new PackageId(group, artifact),
                version,
                "maven-central",
                scope,
                false,
                Optional.of(base + ".jar"),
                Optional.of(base + ".pom"),
                Optional.of(jarSha256),
                Optional.empty(),
                dependencies);
    }

    private static LockPackage workspacePackage(String group, String artifact, String version) {
        return new LockPackage(
                new PackageId(group, artifact),
                version,
                "workspace",
                DependencyScope.COMPILE,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(artifact),
                Optional.of("target/classes"),
                List.of());
    }
}
