package com.zolt.publish;

import com.zolt.build.PackageEvidenceManifest;
import com.zolt.build.PackageEvidenceManifestReader;
import com.zolt.build.PackageEvidenceManifestWriter;
import com.zolt.build.PackageException;
import com.zolt.build.PackagePlan;
import com.zolt.build.PackagePlanService;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.project.ProjectConfig;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class PublishDryRunService {
    private final ZoltTomlParser projectParser;
    private final PublishSettingsReader publishSettingsReader;
    private final PackagePlanService packagePlanService;
    private final PackageEvidenceManifestReader evidenceManifestReader;
    private final Function<String, String> environment;

    public PublishDryRunService() {
        this(
                new ZoltTomlParser(),
                new PublishSettingsReader(),
                new PackagePlanService(),
                new PackageEvidenceManifestReader(),
                System::getenv);
    }

    PublishDryRunService(Function<String, String> environment) {
        this(
                new ZoltTomlParser(),
                new PublishSettingsReader(),
                new PackagePlanService(),
                new PackageEvidenceManifestReader(),
                environment);
    }

    PublishDryRunService(
            ZoltTomlParser projectParser,
            PublishSettingsReader publishSettingsReader,
            PackagePlanService packagePlanService,
            PackageEvidenceManifestReader evidenceManifestReader,
            Function<String, String> environment) {
        this.projectParser = projectParser;
        this.publishSettingsReader = publishSettingsReader;
        this.packagePlanService = packagePlanService;
        this.evidenceManifestReader = evidenceManifestReader;
        this.environment = environment;
    }

    public PublishDryRunPlan plan(Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        ProjectConfig config = projectParser.parse(root.resolve("zolt.toml"));
        PublishSettings publish = publishSettingsReader.read(root.resolve("zolt.toml"), config.repositoryCredentials());
        if (!publish.configured()) {
            throw new PublishException("No [publish] configuration found. Add release/snapshot publish repositories before running `zolt publish --dry-run`.");
        }
        if (!publish.artifacts().equals(List.of("main"))) {
            throw new PublishException("Only [publish].artifacts = [\"main\"] is supported for the first dry-run slice.");
        }
        String versionKind = config.project().version().endsWith("-SNAPSHOT") ? "snapshot" : "release";
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
        blockers.addAll(credentialBlockers(repository, config.repositoryCredentials()));

        PackagePlan packagePlan = packagePlan(root, config);
        Path artifactPath = packagePlan.archivePath();
        Path evidencePath = PackageEvidenceManifestWriter.evidenceManifestPath(artifactPath);
        String artifactSha256 = "";
        if (!Files.isRegularFile(artifactPath)) {
            blockers.add("missing artifact: run `zolt package` to create " + displayPath(root, artifactPath));
        } else if (!Files.isRegularFile(evidencePath)) {
            blockers.add("missing package evidence: run `zolt package` to create " + displayPath(root, evidencePath));
        } else {
            try {
                PackageEvidenceManifest evidence = evidenceManifestReader.read(evidencePath);
                artifactSha256 = evidence.archiveSha256();
                String actualSha256 = sha256(artifactPath);
                if (!actualSha256.equals(evidence.archiveSha256())) {
                    blockers.add("stale package evidence: run `zolt package` to refresh " + displayPath(root, evidencePath));
                }
            } catch (PackageException exception) {
                blockers.add("invalid package evidence: " + exception.getMessage());
            }
        }

        return new PublishDryRunPlan(
                config.project().group() + ":" + config.project().name() + ":" + config.project().version(),
                versionKind,
                repository.id(),
                repository.url(),
                "main",
                display(root, artifactPath),
                artifactSha256,
                display(root, evidencePath),
                blockers);
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
