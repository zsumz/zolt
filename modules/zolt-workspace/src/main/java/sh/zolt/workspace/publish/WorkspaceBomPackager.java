package sh.zolt.workspace.publish;

import sh.zolt.build.BuildResult;
import sh.zolt.build.packaging.PackageResult;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.PackageMode;
import sh.zolt.project.ProjectConfig;
import sh.zolt.publish.PublishPomGenerator;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceMember;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Packages a BOM member: no compile, no jar. Generates the {@code <dependencyManagement>} POM into
 * {@code target/publish/<name>-<version>.pom} and records package evidence with the POM's sha256, so
 * publish (and users) have a durable, checksummed artifact.
 */
public final class WorkspaceBomPackager {
    private static final String EVIDENCE_SCHEMA = "zolt.bom-package-evidence.v1";

    private final WorkspaceBomFamily family = new WorkspaceBomFamily();
    private final PublishPomGenerator pomGenerator = new PublishPomGenerator();

    public PackageResult packageBom(
            WorkspaceMember bomMember, Workspace workspace, ZoltLockfile aggregatedLock, BuildResult buildResult) {
        ZoltLockfile familyLock = family.familyLock(workspace, aggregatedLock, bomMember);
        return write(bomMember.directory(), bomMember.config(), familyLock, buildResult);
    }

    /** Writes the BOM POM + evidence for a standalone (non-workspace) BOM with no family members. */
    public PackageResult packageStandaloneBom(Path projectDirectory, ProjectConfig config, BuildResult buildResult) {
        return write(projectDirectory, config, new ZoltLockfile(1, List.of(), List.of()), buildResult);
    }

    private PackageResult write(
            Path projectDirectory, ProjectConfig config, ZoltLockfile familyLock, BuildResult buildResult) {
        String artifactBase = config.project().name() + "-" + config.project().version();
        Path publishDirectory = projectDirectory.resolve(config.build().outputRoot()).resolve("publish");
        Path pomPath = publishDirectory.resolve(artifactBase + ".pom");
        Path evidencePath = publishDirectory.resolve(artifactBase + ".pom.zolt-package.json");
        try {
            Files.createDirectories(publishDirectory);
            Files.writeString(pomPath, pomGenerator.generate(config, familyLock));
            String pomSha256 = "sha256:" + sha256(pomPath);
            Files.writeString(evidencePath, evidenceJson(config, artifactBase + ".pom", pomSha256));
        } catch (IOException exception) {
            throw new sh.zolt.workspace.WorkspaceConfigException(
                    "Could not write BOM package artifact at " + pomPath + ": " + exception.getMessage());
        }
        return new PackageResult(
                buildResult,
                PackageMode.BOM,
                pomPath,
                Optional.empty(),
                Optional.of(evidencePath),
                0,
                false,
                "pom",
                List.of(),
                List.of());
    }

    private static String evidenceJson(ProjectConfig config, String pomFile, String pomSha256) {
        String coordinate = config.project().group() + ":" + config.project().name() + ":" + config.project().version();
        return "{\n"
                + "  \"schema\": \"" + EVIDENCE_SCHEMA + "\",\n"
                + "  \"coordinate\": \"" + coordinate + "\",\n"
                + "  \"pom\": \"" + pomFile + "\",\n"
                + "  \"pomSha256\": \"" + pomSha256 + "\"\n"
                + "}\n";
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
