package sh.zolt.release.archive;

import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectPathException;
import sh.zolt.project.ProjectPaths;
import sh.zolt.provenance.BuildProvenance;
import sh.zolt.provenance.BuildProvenanceSource;
import sh.zolt.release.ReleaseTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ReleaseArchiveService {
    private final ReleaseArchiveManifestWriter manifestWriter;
    private final ReleaseArchiveLayout layout;
    private final ReleaseArchiveProvenanceJson provenanceJson;
    private final ReleaseArchiveWriter archiveWriter;
    private final BuildProvenanceSource provenanceSource;
    private final Clock clock;
    private final Map<String, String> environment;

    public ReleaseArchiveService() {
        this(BuildProvenanceSource.empty());
    }

    public ReleaseArchiveService(BuildProvenanceSource provenanceSource) {
        this(new ReleaseArchiveManifestWriter(), provenanceSource, Clock.systemUTC(), System.getenv());
    }

    ReleaseArchiveService(ReleaseArchiveManifestWriter manifestWriter) {
        this(manifestWriter, BuildProvenanceSource.empty(), Clock.systemUTC(), System.getenv());
    }

    ReleaseArchiveService(
            ReleaseArchiveManifestWriter manifestWriter,
            BuildProvenanceSource provenanceSource,
            Clock clock,
            Map<String, String> environment) {
        this.manifestWriter = manifestWriter;
        this.layout = new ReleaseArchiveLayout();
        this.provenanceJson = new ReleaseArchiveProvenanceJson();
        this.archiveWriter = new ReleaseArchiveWriter();
        this.provenanceSource = provenanceSource == null ? BuildProvenanceSource.empty() : provenanceSource;
        this.clock = clock;
        this.environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    public ReleaseArchiveResult assemble(
            Path projectDirectory,
            ProjectConfig config,
            ReleaseTarget target,
            Path binaryPath,
            Path outputDirectory) {
        Path projectRoot = ProjectPaths.root(projectDirectory);
        Path binary = releaseBinaryInput(projectRoot, "--binary", binaryPath);
        if (!Files.isRegularFile(binary)) {
            throw new ReleaseArchiveException(
                    "Release archive requires native binary at " + binary
                            + ". Run `zolt native` or pass --binary <path>.");
        }

        String releaseBaseName = releaseBaseName(config);
        String rootDirectory = releaseBaseName + "-" + target.id();
        Path output = releaseOutput(projectRoot, "--output", outputDirectory.toString());
        Path archivePath = output.resolve(rootDirectory + target.archiveExtension());
        String version = ProjectPaths.filenameComponent("[project].version", config.project().version());
        BuildProvenance provenance = provenanceSource.read(projectRoot, environment, clock);
        Optional<BuildProvenance> releaseProvenance = provenance.zoltVersion().isBlank()
                ? Optional.empty()
                : Optional.of(provenance);
        List<ReleaseArchiveEntry> entries = layout.entries(
                projectRoot,
                binary,
                rootDirectory,
                target.binaryName(),
                version,
                releaseProvenance.map(value -> provenanceJson.write(config, target, value)));

        try {
            Files.createDirectories(output);
            archiveWriter.write(archivePath, entries, target.zip());
            String checksum = manifestWriter.checksum(archivePath);
            Path checksumPath = manifestWriter.writeChecksum(archivePath, checksum);
            Path manifestPath = manifestWriter.writeManifest(
                    output,
                    ProjectPaths.filenameComponent("[project].name", config.project().name()),
                    version,
                    releaseProvenance);
            return new ReleaseArchiveResult(
                    target,
                    archivePath,
                    checksumPath,
                    manifestPath,
                    rootDirectory,
                    checksum,
                    archiveWriter.fileCount(entries));
        } catch (IOException exception) {
            throw new ReleaseArchiveException(
                    "Could not write release archive " + archivePath + ". Check that the output directory is writable.",
                    exception);
        }
    }

    private static Path releaseBinaryInput(Path projectRoot, String key, Path configuredPath) {
        if (!configuredPath.isAbsolute()) {
            return releaseInput(projectRoot, key, configuredPath.toString());
        }
        Path binary = configuredPath.normalize();
        if (!binary.startsWith(projectRoot)) {
            throw invalidReleaseBinaryPath(projectRoot, key, configuredPath.toString(), binary);
        }
        if (Files.exists(binary)) {
            try {
                ProjectPaths.requireExistingInsideProject(projectRoot, key, configuredPath.toString(), binary);
            } catch (ProjectPathException exception) {
                throw new ReleaseArchiveException(exception.getMessage(), exception);
            }
        }
        return binary;
    }

    private static Path releaseInput(Path projectRoot, String key, String configuredPath) {
        try {
            return ProjectPaths.input(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new ReleaseArchiveException(exception.getMessage(), exception);
        }
    }

    private static Path releaseOutput(Path projectRoot, String key, String configuredPath) {
        Path configured = Path.of(configuredPath);
        if (configured.isAbsolute()) {
            Path output = configured.normalize();
            if (!output.startsWith(projectRoot) || output.equals(projectRoot)) {
                throw invalidReleaseOutputPath(projectRoot, key, configuredPath, output);
            }
            if (Files.exists(output)) {
                requireExistingReleaseOutputInsideProject(projectRoot, key, configuredPath, output);
            } else {
                requireExistingReleaseOutputAncestorInsideProject(projectRoot, key, configuredPath, output);
            }
            return output;
        }
        try {
            return ProjectPaths.output(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new ReleaseArchiveException(exception.getMessage(), exception);
        }
    }

    private static void requireExistingReleaseOutputAncestorInsideProject(
            Path projectRoot,
            String key,
            String configuredPath,
            Path output) {
        Path ancestor = output.getParent();
        while (ancestor != null && !Files.exists(ancestor)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor != null) {
            requireExistingReleaseOutputInsideProject(projectRoot, key, configuredPath, ancestor);
        }
    }

    private static void requireExistingReleaseOutputInsideProject(
            Path projectRoot,
            String key,
            String configuredPath,
            Path output) {
        try {
            ProjectPaths.requireExistingInsideProject(projectRoot, key, configuredPath, output);
        } catch (ProjectPathException exception) {
            throw new ReleaseArchiveException(exception.getMessage(), exception);
        }
    }

    private static ReleaseArchiveException invalidReleaseBinaryPath(
            Path projectRoot,
            String key,
            String configuredPath,
            Path resolvedPath) {
        return new ReleaseArchiveException(
                "Invalid "
                        + key
                        + " path `"
                        + configuredPath
                        + "` resolved to "
                        + resolvedPath
                        + ". Use a project-relative path or an absolute path under "
                        + projectRoot
                        + ".");
    }

    private static ReleaseArchiveException invalidReleaseOutputPath(
            Path projectRoot,
            String key,
            String configuredPath,
            Path resolvedPath) {
        return new ReleaseArchiveException(
                "Invalid "
                        + key
                        + " path `"
                        + configuredPath
                        + "` resolved to "
                        + resolvedPath
                        + ". Use a project-relative path or an absolute path under "
                        + projectRoot
                        + ".");
    }

    private static String releaseBaseName(ProjectConfig config) {
        try {
            return ProjectPaths.filenameComponent("[project].name", config.project().name())
                    + "-"
                    + ProjectPaths.filenameComponent("[project].version", config.project().version());
        } catch (ProjectPathException exception) {
            throw new ReleaseArchiveException(exception.getMessage(), exception);
        }
    }

}
