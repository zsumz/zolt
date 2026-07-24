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
    private final PublishSettingsReader publishSettingsReader;

    public PublishCentralReadinessService() {
        this(new ZoltTomlParser(), new PublishSettingsReader());
    }

    PublishCentralReadinessService(ZoltTomlParser projectParser, PublishSettingsReader publishSettingsReader) {
        this.projectParser = projectParser;
        this.publishSettingsReader = publishSettingsReader;
    }

    public List<PublishCentralRequirement> evaluate(Path projectRoot, PublishDryRunPlan plan) {
        Path root = projectRoot.toAbsolutePath().normalize();
        ProjectConfig config = projectParser.parse(root.resolve("zolt.toml"));
        PublishSettings publish = publishSettingsReader.read(root.resolve("zolt.toml"), config.repositoryCredentials());
        return evaluate(config, publish, plan);
    }

    /**
     * Evaluates Central readiness from already-resolved config and publish settings. The workspace
     * publish path supplies the policy-merged member config so inherited {@code [package.metadata]}
     * and {@code [publish.signing]} are honoured without re-reading the member {@code zolt.toml}.
     */
    public List<PublishCentralRequirement> evaluate(
            ProjectConfig config, PublishSettings publish, PublishDryRunPlan plan) {
        PublicationMetadata metadata = config.packageSettings().metadata();
        return PublishCentralReadiness.evaluate(
                plan.versionKind(),
                metadata,
                hasClassifier(plan, "sources"),
                hasClassifier(plan, "javadoc"),
                publish.signing().enabled(),
                config.packageSettings().mode() == sh.zolt.project.PackageMode.BOM);
    }

    private static boolean hasClassifier(PublishDryRunPlan plan, String classifier) {
        return plan.supplementalArtifacts().stream()
                .anyMatch(artifact -> artifact.classifier().map(classifier::equals).orElse(false));
    }
}
