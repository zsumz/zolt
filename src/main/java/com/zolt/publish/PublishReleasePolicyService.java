package com.zolt.publish;

import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.PublicationMetadata;
import com.zolt.project.VersionPolicy;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PublishReleasePolicyService {
    private final ZoltTomlParser projectParser;

    public PublishReleasePolicyService() {
        this(new ZoltTomlParser());
    }

    PublishReleasePolicyService(ZoltTomlParser projectParser) {
        this.projectParser = projectParser;
    }

    public PublishDryRunPlan apply(Path projectRoot, PublishDryRunPlan plan) {
        ProjectConfig config = projectParser.parse(projectRoot.toAbsolutePath().normalize().resolve("zolt.toml"));
        List<String> blockers = new ArrayList<>();
        if (VersionPolicy.violation(
                VersionPolicy.Context.PUBLISH_RELEASE,
                config.project().version()).isPresent()) {
            blockers.add("release context rejects SNAPSHOT version `"
                    + config.project().version()
                    + "`. Use a non-SNAPSHOT version for release publishing.");
        }
        PackageSettings settings = config.packageSettings();
        addMetadataBlockers(settings.metadata(), blockers);
        Set<String> supplementalIds = plan.supplementalArtifacts().stream()
                .map(PublishArtifactPlan::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!settings.sources() || !supplementalIds.contains("sources")) {
            blockers.add("release context requires a sources jar from `zolt package`; set [package].sources = true and run `zolt package`.");
        }
        if (!settings.javadoc() || !supplementalIds.contains("javadoc")) {
            blockers.add("release context requires a javadoc jar from `zolt package`; set [package].javadoc = true and run `zolt package`.");
        }
        return plan.withContext("release", blockers);
    }

    private static void addMetadataBlockers(PublicationMetadata metadata, List<String> blockers) {
        if (metadata.name().isBlank()) {
            blockers.add("release context requires [package.metadata].name.");
        }
        if (metadata.description().isBlank()) {
            blockers.add("release context requires [package.metadata].description.");
        }
        if (metadata.url().isBlank()) {
            blockers.add("release context requires [package.metadata].url.");
        }
        if (metadata.license().isBlank()) {
            blockers.add("release context requires [package.metadata].license.");
        }
        if (metadata.developers().isEmpty()) {
            blockers.add("release context requires at least one [package.metadata].developers entry.");
        }
        if (metadata.scm().isBlank()) {
            blockers.add("release context requires [package.metadata].scm.");
        }
        if (metadata.issues().isBlank()) {
            blockers.add("release context requires [package.metadata].issues.");
        }
    }
}
