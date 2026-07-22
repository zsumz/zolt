package sh.zolt.publish;

import sh.zolt.project.ProjectConfig;
import sh.zolt.project.PublicationMetadata;
import sh.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.util.List;

/**
 * Gathers the inputs a {@link PublishCentralReadiness} evaluation needs — publication metadata and
 * version from {@code zolt.toml}, sources/Javadoc presence from an already-computed dry-run plan —
 * and produces the Maven Central requirement report.
 */
public final class PublishCentralReadinessService {
    private final ZoltTomlParser projectParser;

    public PublishCentralReadinessService() {
        this(new ZoltTomlParser());
    }

    PublishCentralReadinessService(ZoltTomlParser projectParser) {
        this.projectParser = projectParser;
    }

    public List<PublishCentralRequirement> evaluate(Path projectRoot, PublishDryRunPlan plan) {
        Path root = projectRoot.toAbsolutePath().normalize();
        ProjectConfig config = projectParser.parse(root.resolve("zolt.toml"));
        PublicationMetadata metadata = config.packageSettings().metadata();
        return PublishCentralReadiness.evaluate(
                plan.versionKind(),
                metadata,
                hasClassifier(plan, "sources"),
                hasClassifier(plan, "javadoc"),
                signaturesConfigured(config));
    }

    private static boolean signaturesConfigured(ProjectConfig config) {
        // Wired to real signing configuration when GPG signing is available.
        return false;
    }

    private static boolean hasClassifier(PublishDryRunPlan plan, String classifier) {
        return plan.supplementalArtifacts().stream()
                .anyMatch(artifact -> artifact.classifier().map(classifier::equals).orElse(false));
    }
}
