package sh.zolt.quarkus.bootstrap;

import sh.zolt.quarkus.QuarkusDeploymentArtifact;
import sh.zolt.quarkus.QuarkusMetadataException;
import java.util.List;
import java.util.Map;

public record QuarkusExtensionMetadata(
        QuarkusDeploymentArtifact deploymentArtifact,
        List<QuarkusArtifactKey> parentFirstArtifacts,
        List<QuarkusArtifactKey> runnerParentFirstArtifacts,
        List<QuarkusArtifactKey> excludedArtifacts,
        List<QuarkusArtifactKey> lesserPriorityArtifacts,
        Map<QuarkusArtifactKey, List<String>> removedResources,
        List<String> providesCapabilities,
        List<String> requiresCapabilities,
        List<String> conditionalDependencies) {
    public QuarkusExtensionMetadata {
        if (deploymentArtifact == null) {
            throw new QuarkusMetadataException(
                    "Missing required Quarkus extension metadata field `deployment-artifact`.");
        }
        parentFirstArtifacts = List.copyOf(parentFirstArtifacts);
        runnerParentFirstArtifacts = List.copyOf(runnerParentFirstArtifacts);
        excludedArtifacts = List.copyOf(excludedArtifacts);
        lesserPriorityArtifacts = List.copyOf(lesserPriorityArtifacts);
        removedResources = Map.copyOf(removedResources);
        providesCapabilities = List.copyOf(providesCapabilities);
        requiresCapabilities = List.copyOf(requiresCapabilities);
        conditionalDependencies = List.copyOf(conditionalDependencies);
    }
}
