package com.zolt.publish;

import com.zolt.build.PackageEvidenceArtifact;
import com.zolt.build.PackageEvidenceManifest;
import com.zolt.build.PackageEvidenceManifestReader;
import com.zolt.build.PackageEvidenceManifestWriter;
import com.zolt.build.PackageException;
import com.zolt.build.PackagePlan;
import com.zolt.build.PackagePlanService;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.MavenRepositoryPathBuilder;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.VersionPolicy;
import com.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class PublishDryRunService {
    private static final Set<PackageMode> SINGLE_FILE_PACKAGE_ARTIFACTS = Set.of(
            PackageMode.THIN,
            PackageMode.SPRING_BOOT,
            PackageMode.WAR,
            PackageMode.SPRING_BOOT_WAR);

    private final ZoltTomlParser projectParser;
    private final PublishSettingsReader publishSettingsReader;
    private final PackagePlanService packagePlanService;
    private final PackageEvidenceManifestReader evidenceManifestReader;
    private final ZoltLockfileReader lockfileReader;
    private final PublishPomGenerator pomGenerator;
    private final MavenRepositoryPathBuilder repositoryPathBuilder;
    private final Function<String, String> environment;

    public PublishDryRunService() {
        this(
                new ZoltTomlParser(),
                new PublishSettingsReader(),
                new PackagePlanService(),
                new PackageEvidenceManifestReader(),
                new ZoltLockfileReader(),
                new PublishPomGenerator(),
                new MavenRepositoryPathBuilder(),
                System::getenv);
    }

    PublishDryRunService(Function<String, String> environment) {
        this(
                new ZoltTomlParser(),
                new PublishSettingsReader(),
                new PackagePlanService(),
                new PackageEvidenceManifestReader(),
                new ZoltLockfileReader(),
                new PublishPomGenerator(),
                new MavenRepositoryPathBuilder(),
                environment);
    }

    PublishDryRunService(
            ZoltTomlParser projectParser,
            PublishSettingsReader publishSettingsReader,
            PackagePlanService packagePlanService,
            PackageEvidenceManifestReader evidenceManifestReader,
            ZoltLockfileReader lockfileReader,
            PublishPomGenerator pomGenerator,
            MavenRepositoryPathBuilder repositoryPathBuilder,
            Function<String, String> environment) {
        this.projectParser = projectParser;
        this.publishSettingsReader = publishSettingsReader;
        this.packagePlanService = packagePlanService;
        this.evidenceManifestReader = evidenceManifestReader;
        this.lockfileReader = lockfileReader;
        this.pomGenerator = pomGenerator;
        this.repositoryPathBuilder = repositoryPathBuilder;
        this.environment = environment;
    }

    public PublishDryRunPlan plan(Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        ProjectConfig config = projectParser.parse(root.resolve("zolt.toml"));
        PublishSettings publish = publishSettingsReader.read(root.resolve("zolt.toml"), config.repositoryCredentials());
        if (!publish.configured()) {
            throw new PublishException("No [publish] configuration found. Add release/snapshot publish repositories before running `zolt publish --dry-run`.");
        }
        String artifactId = selectedArtifactId(publish.artifacts(), config.packageSettings().mode());
        String versionKind = VersionPolicy.classifyPublishVersion(config.project().version());
        String repositoryId = versionKind.equals("snapshot")
                ? publish.snapshotRepository()
                : publish.releaseRepository();
        if (repositoryId.isBlank()) {
            throw new PublishException("Project version `"
                    + config.project().version()
                    + "` requires [publish]."
                    + (versionKind.equals("snapshot") ? "snapshotRepository" : "releaseRepository")
                    + " for `zolt publish --dry-run`.");
        }
        PublishRepositorySettings repository = publish.repositories().get(repositoryId);
        if (repository == null) {
            throw new PublishException("Publish repository `" + repositoryId + "` is not defined.");
        }
        List<String> blockers = new ArrayList<>();
        if (hasEmbeddedCredentials(repository.url())) {
            blockers.add("publish repository `"
                    + repository.id()
                    + "` URL contains embedded credentials. Move credentials to [repositoryCredentials] environment references.");
        }
        blockers.addAll(credentialBlockers(repository, config.repositoryCredentials()));

        ZoltLockfile lockfile = lockfile(root);
        PackagePlan packagePlan = packagePlan(root, config);
        Path artifactPath = packagePlan.archivePath();
        Path evidencePath = PackageEvidenceManifestWriter.evidenceManifestPath(artifactPath);
        Path pomPath = root.resolve("target/publish")
                .resolve(config.project().name() + "-" + config.project().version() + ".pom")
                .normalize();
        String pomSha256 = writePom(root, pomPath, config, lockfile);
        Coordinate coordinate = coordinate(config);
        String artifactUploadPath = repositoryPathBuilder.artifactPath(
                new ArtifactDescriptor(coordinate, Optional.empty(), extension(artifactPath)));
        String pomUploadPath = repositoryPathBuilder.pomPath(coordinate);
        String artifactSha256 = "";
        List<PublishArtifactPlan> supplementalArtifacts = List.of();
        if (!Files.isRegularFile(artifactPath)) {
            blockers.add("missing artifact: run `zolt package` to create " + displayPath(root, artifactPath));
        } else if (!Files.isRegularFile(evidencePath)) {
            blockers.add("missing package evidence: run `zolt package` to create " + displayPath(root, evidencePath));
        } else {
            try {
                PackageEvidenceManifest evidence = evidenceManifestReader.read(evidencePath);
                artifactSha256 = evidence.archiveSha256();
                Path evidenceArchive = root.resolve(evidence.archive()).normalize();
                if (!evidenceArchive.equals(artifactPath)) {
                    blockers.add("package evidence archive mismatch: "
                            + displayPath(root, evidencePath)
                            + " describes "
                            + displayPath(root, evidenceArchive)
                            + " but publish selected "
                            + displayPath(root, artifactPath)
                            + ". Run `zolt package` to refresh package evidence.");
                }
                String actualSha256 = sha256(artifactPath);
                if (!actualSha256.equals(evidence.archiveSha256())) {
                    blockers.add("stale package evidence: run `zolt package` to refresh " + displayPath(root, evidencePath));
                }
                supplementalArtifacts = supplementalArtifacts(root, coordinate, evidence, evidencePath, blockers);
            } catch (PackageException exception) {
                blockers.add("invalid package evidence: " + exception.getMessage());
            }
        }

        return new PublishDryRunPlan(
                config.project().group() + ":" + config.project().name() + ":" + config.project().version(),
                versionKind,
                repository.id(),
                redactedRepositoryUrl(repository.url()),
                artifactId,
                display(root, artifactPath),
                artifactSha256,
                artifactUploadPath,
                supplementalArtifacts,
                display(root, evidencePath),
                display(root, pomPath),
                pomSha256,
                pomUploadPath,
                "",
                blockers);
    }

    private List<PublishArtifactPlan> supplementalArtifacts(
            Path root,
            Coordinate coordinate,
            PackageEvidenceManifest evidence,
            Path evidencePath,
            List<String> blockers) {
        List<PublishArtifactPlan> artifacts = new ArrayList<>();
        for (PackageEvidenceArtifact artifact : evidence.artifacts()) {
            if ("main".equals(artifact.classifier())) {
                continue;
            }
            Path artifactPath = root.resolve(artifact.path()).normalize();
            String uploadPath = repositoryPathBuilder.artifactPath(new ArtifactDescriptor(
                    coordinate,
                    Optional.of(artifact.classifier()),
                    extension(artifactPath)));
            if (!Files.isRegularFile(artifactPath)) {
                blockers.add("missing supplemental artifact: run `zolt package` to create " + displayPath(root, artifactPath));
                continue;
            }
            String actualSha256 = sha256(artifactPath);
            if (!actualSha256.equals(artifact.sha256())) {
                blockers.add("stale supplemental package evidence: run `zolt package` to refresh " + displayPath(root, evidencePath));
            }
            artifacts.add(new PublishArtifactPlan(
                    artifact.classifier(),
                    Optional.of(artifact.classifier()),
                    display(root, artifactPath),
                    artifact.sha256(),
                    uploadPath));
        }
        return List.copyOf(artifacts);
    }

    private static Coordinate coordinate(ProjectConfig config) {
        return new Coordinate(
                config.project().group(),
                config.project().name(),
                Optional.of(config.project().version()));
    }

    private static String extension(Path artifactPath) {
        String fileName = artifactPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new PublishException("Could not determine publish artifact extension from `" + fileName + "`.");
        }
        return fileName.substring(dot + 1);
    }

    private static boolean hasEmbeddedCredentials(String url) {
        try {
            URI uri = new URI(url);
            return uri.getRawUserInfo() != null && !uri.getRawUserInfo().isBlank();
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static String redactedRepositoryUrl(String url) {
        try {
            URI uri = new URI(url);
            String userInfo = uri.getRawUserInfo();
            if (userInfo == null || userInfo.isBlank()) {
                return url;
            }
            return url.replace(userInfo + "@", "***@");
        } catch (URISyntaxException exception) {
            return url;
        }
    }

    private static String selectedArtifactId(List<String> artifacts, PackageMode packageMode) {
        if (artifacts.size() != 1) {
            throw new PublishException("zolt publish --dry-run currently supports one package artifact selector. Use [publish].artifacts = [\"main\"] for the configured package output, or [\""
                    + packageMode.configValue()
                    + "\"] to select it explicitly.");
        }
        String artifact = artifacts.getFirst();
        if (artifact.equals("main")) {
            return artifact;
        }
        PackageMode selectedMode = PackageMode.fromConfigValue(artifact)
                .orElseThrow(() -> new PublishException("Unsupported publish artifact selector `"
                        + artifact
                        + "`. Use `main` or one of the package mode selectors: thin, spring-boot, war, spring-boot-war."));
        if (!SINGLE_FILE_PACKAGE_ARTIFACTS.contains(selectedMode)) {
            throw new PublishException("Publish artifact selector `"
                    + artifact
                    + "` does not describe a single package archive yet. Use `main`, `thin`, `spring-boot`, `war`, or `spring-boot-war`.");
        }
        if (selectedMode != packageMode) {
            throw new PublishException("Publish artifact selector `"
                    + artifact
                    + "` requires [package].mode = \""
                    + artifact
                    + "\", but the current package mode is `"
                    + packageMode.configValue()
                    + "`.");
        }
        return artifact;
    }

    private ZoltLockfile lockfile(Path root) {
        try {
            return lockfileReader.read(root.resolve("zolt.lock"));
        } catch (LockfileReadException exception) {
            throw new PublishException("Could not read zolt.lock for publish metadata: " + exception.getMessage());
        }
    }

    private PackagePlan packagePlan(Path root, ProjectConfig config) {
        try {
            return packagePlanService.plan(root, config, root.resolve("zolt.lock"));
        } catch (LockfileReadException exception) {
            throw new PublishException("Could not plan publish artifact: " + exception.getMessage());
        }
    }

    private List<String> credentialBlockers(
            PublishRepositorySettings repository,
            Map<String, RepositoryCredentialSettings> credentialSettings) {
        if (repository.credentials().isEmpty()) {
            return List.of();
        }
        RepositoryCredentialSettings credential = credentialSettings.get(repository.credentials().orElseThrow());
        if (credential == null) {
            return List.of("missing credential metadata for publish repository `" + repository.id() + "`");
        }
        List<String> missing = new ArrayList<>();
        if (missingEnvironment(credential.usernameEnv())) {
            missing.add(credential.usernameEnv());
        }
        if (missingEnvironment(credential.passwordEnv())) {
            missing.add(credential.passwordEnv());
        }
        if (missing.isEmpty()) {
            return List.of();
        }
        return List.of("missing credential environment "
                + (missing.size() == 1 ? "variable " : "variables ")
                + String.join(", ", missing)
                + " for publish repository `"
                + repository.id()
                + "`");
    }

    private boolean missingEnvironment(String name) {
        String value = environment.apply(name);
        return value == null || value.isBlank();
    }

    private String writePom(Path root, Path pomPath, ProjectConfig config, ZoltLockfile lockfile) {
        try {
            Files.createDirectories(pomPath.getParent());
            Files.writeString(pomPath, pomGenerator.generate(config, lockfile));
            return sha256(pomPath);
        } catch (IOException exception) {
            throw new PublishException(
                    "Could not write publish POM preview at " + displayPath(root, pomPath) + ".",
                    exception);
        }
    }

    private static Path display(Path root, Path path) {
        return Path.of(displayPath(root, path));
    }

    private static String sha256(Path path) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (java.io.IOException exception) {
            throw new PublishException("Could not read package artifact at " + path + ".", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new PublishException("Could not compute package artifact checksum because SHA-256 is unavailable.", exception);
        }
    }

    private static String displayPath(Path root, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(root)) {
            return root.relativize(normalized).toString();
        }
        return normalized.toString();
    }
}
