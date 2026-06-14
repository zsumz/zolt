package com.zolt.quarkus;

import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.dependency.PackageId;
import com.zolt.resolve.DependencyRequest;
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.FrameworkDependencyCandidate;
import com.zolt.resolve.FrameworkDependencyRequestPlanRequest;
import com.zolt.resolve.FrameworkDependencyRequestPlanner;
import com.zolt.resolve.RequestOrigin;
import com.zolt.resolve.ResolveException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipException;

public final class QuarkusDependencyRequestPlanner implements FrameworkDependencyRequestPlanner {
    private final QuarkusExtensionMetadataReader metadataReader;

    public QuarkusDependencyRequestPlanner() {
        this(new QuarkusExtensionMetadataReader());
    }

    QuarkusDependencyRequestPlanner(QuarkusExtensionMetadataReader metadataReader) {
        this.metadataReader = metadataReader;
    }

    @Override
    public List<DependencyRequest> plan(FrameworkDependencyRequestPlanRequest request) {
        if (!request.config().frameworkSettings().quarkus().enabled()) {
            return List.of();
        }
        Map<String, DependencyRequest> requests = new LinkedHashMap<>();
        request.candidates().stream()
                .filter(FrameworkDependencyCandidate::entersPackagedMainRuntimeClasspath)
                .sorted(Comparator.comparing(candidate -> candidate.packageId() + ":" + candidate.selectedVersion()))
                .forEach(candidate -> addDeploymentRequests(request, candidate, requests));
        for (DependencyRequest platformPropertiesRequest : request.platformPropertiesRequests().get()) {
            requests.put(requestKey(platformPropertiesRequest), platformPropertiesRequest);
        }
        return List.copyOf(requests.values());
    }

    private void addDeploymentRequests(
            FrameworkDependencyRequestPlanRequest request,
            FrameworkDependencyCandidate candidate,
            Map<String, DependencyRequest> requests) {
        Coordinate coordinate = new Coordinate(
                candidate.packageId().groupId(),
                candidate.packageId().artifactId(),
                Optional.of(candidate.selectedVersion()));
        Path jar = request.artifactPathResolver().jarPath(coordinate);
        Optional<QuarkusExtensionMetadata> metadata = quarkusMetadata(jar);
        metadata.ifPresent(quarkusMetadata -> {
            QuarkusDeploymentArtifact artifact = quarkusMetadata.deploymentArtifact();
            if (!"jar".equals(artifact.type())) {
                throw new ResolveException(
                        "Quarkus extension "
                                + coordinate
                                + " declares deployment artifact "
                                + artifact
                                + ", but Zolt currently supports only jar deployment artifacts. "
                                + "Remove that extension or wait for type-aware artifact resolution.");
            }
            DependencyRequest deploymentRequest = new DependencyRequest(
                    new PackageId(artifact.groupId(), artifact.artifactId()),
                    artifact.version(),
                    DependencyScope.QUARKUS_DEPLOYMENT,
                    RequestOrigin.TRANSITIVE,
                    Optional.of(ArtifactDescriptor.jar(
                            new Coordinate(
                                    artifact.groupId(),
                                    artifact.artifactId(),
                                    Optional.of(artifact.version())),
                            artifact.classifier())));
            requests.put(requestKey(deploymentRequest), deploymentRequest);
            for (QuarkusArtifactKey artifactKey : quarkusMetadata.parentFirstArtifacts()) {
                parentFirstDeploymentRequest(artifactKey, request.selectedVersions(), request.managedVersions())
                        .ifPresent(parentFirstRequest -> requests.put(requestKey(parentFirstRequest), parentFirstRequest));
            }
            for (QuarkusArtifactKey artifactKey : quarkusMetadata.runnerParentFirstArtifacts()) {
                parentFirstDeploymentRequest(artifactKey, request.selectedVersions(), request.managedVersions())
                        .ifPresent(parentFirstRequest -> requests.put(requestKey(parentFirstRequest), parentFirstRequest));
            }
        });
    }

    private Optional<DependencyRequest> parentFirstDeploymentRequest(
            QuarkusArtifactKey artifactKey,
            Map<PackageId, String> selectedVersions,
            Map<PackageId, String> managedVersions) {
        if (artifactKey.type().isPresent() && !"jar".equals(artifactKey.type().orElseThrow())) {
            return Optional.empty();
        }
        PackageId packageId = new PackageId(artifactKey.groupId(), artifactKey.artifactId());
        String version = selectedVersions.getOrDefault(packageId, managedVersions.get(packageId));
        if (version == null || version.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new DependencyRequest(
                packageId,
                version,
                DependencyScope.QUARKUS_DEPLOYMENT,
                RequestOrigin.TRANSITIVE,
                Optional.of(ArtifactDescriptor.jar(
                        new Coordinate(packageId.groupId(), packageId.artifactId(), Optional.of(version)),
                        artifactKey.classifier()))));
    }

    private Optional<QuarkusExtensionMetadata> quarkusMetadata(Path jarPath) {
        try {
            return metadataReader.readIfPresent(jarPath);
        } catch (QuarkusMetadataException exception) {
            if (exception.getCause() instanceof ZipException) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private static String requestKey(DependencyRequest request) {
        return request.packageId()
                + ":"
                + request.requestedVersion()
                + ":"
                + request.scope()
                + ":"
                + request.artifactDescriptor()
                        .flatMap(ArtifactDescriptor::classifier)
                        .orElse("")
                + ":"
                + request.artifactDescriptor()
                        .map(ArtifactDescriptor::extension)
                        .orElse("jar");
    }
}
