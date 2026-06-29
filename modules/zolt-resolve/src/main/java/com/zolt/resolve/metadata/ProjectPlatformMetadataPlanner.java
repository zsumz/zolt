package com.zolt.resolve.metadata;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.PomPropertyInterpolator;
import com.zolt.maven.RawPomDependency;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.DependencyRequest;
import com.zolt.resolve.RequestOrigin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class ProjectPlatformMetadataPlanner {
    private final CoordinateParser coordinateParser;
    private final PomPropertyInterpolator interpolator = new PomPropertyInterpolator();

    public ProjectPlatformMetadataPlanner(CoordinateParser coordinateParser) {
        this.coordinateParser = coordinateParser;
    }

    public Map<PackageId, ManagedVersion> managedVersions(
            ProjectConfig config,
            Function<Coordinate, EffectiveRawPom> effectivePomLoader) {
        Map<PackageId, ManagedVersion> details = new LinkedHashMap<>();
        config.platforms().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(platform -> {
                    Coordinate coordinate = coordinateParser.parse(platform.getKey() + ":" + platform.getValue());
                    EffectiveRawPom pom = effectivePomLoader.apply(coordinate);
                    for (RawPomDependency dependency : pom.dependencyManagement()) {
                        if (dependency.classifier().isPresent()) {
                            continue;
                        }
                        RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);
                        if (managedJarDependency(interpolated) && interpolated.version().isPresent()) {
                            PackageId packageId = new PackageId(interpolated.groupId(), interpolated.artifactId());
                            details.put(
                                    packageId,
                                    new ManagedVersion(
                                            interpolated.version().orElseThrow(),
                                            platform.getKey() + ":" + platform.getValue()));
                        }
                    }
                });
        return Map.copyOf(details);
    }

    public List<DependencyRequest> propertiesRequests(
            ProjectConfig config,
            Function<Coordinate, EffectiveRawPom> effectivePomLoader) {
        List<DependencyRequest> requests = new ArrayList<>();
        config.platforms().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(platform -> {
                    Coordinate coordinate = coordinateParser.parse(platform.getKey() + ":" + platform.getValue());
                    EffectiveRawPom pom = effectivePomLoader.apply(coordinate);
                    for (RawPomDependency dependency : pom.dependencyManagement()) {
                        if (dependency.classifier().isPresent()) {
                            continue;
                        }
                        RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);
                        if (!interpolated.type().filter("properties"::equals).isPresent()
                                || interpolated.version().isEmpty()) {
                            continue;
                        }
                        PackageId packageId = new PackageId(interpolated.groupId(), interpolated.artifactId());
                        Optional<String> version = interpolated.version();
                        requests.add(new DependencyRequest(
                                packageId,
                                version.orElseThrow(),
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                RequestOrigin.TRANSITIVE,
                                Optional.of(new ArtifactDescriptor(
                                        new Coordinate(
                                                packageId.groupId(),
                                                packageId.artifactId(),
                                                version),
                                        Optional.empty(),
                                        "properties"))));
                    }
                });
        return List.copyOf(requests);
    }

    private static boolean managedJarDependency(RawPomDependency dependency) {
        return dependency.type().orElse("jar").equals("jar")
                && dependency.classifier().isEmpty();
    }
}
