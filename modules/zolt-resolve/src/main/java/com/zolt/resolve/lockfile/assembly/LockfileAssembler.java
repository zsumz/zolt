package com.zolt.resolve.lockfile.assembly;

import com.zolt.cache.CachedArtifact;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.DependencyPolicyEffect;
import com.zolt.resolve.graph.PackageNode;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.graph.ResolutionGraph;
import com.zolt.resolve.fingerprint.ProjectResolutionFingerprint;
import com.zolt.resolve.metadata.platform.ManagedVersion;
import com.zolt.resolve.request.DependencyRequest;
import com.zolt.resolve.selection.SelectedDependencyScope;
import com.zolt.resolve.selection.SelectedDependencyScopes;
import com.zolt.resolve.version.VersionSelectionResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LockfileAssembler {
    private final CoordinateParser coordinateParser;

    public LockfileAssembler(CoordinateParser coordinateParser) {
        this.coordinateParser = coordinateParser;
    }

    public ZoltLockfile assemble(
            LockfileAssemblyContext context,
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        long started = System.nanoTime();
        try {
            Map<PackageId, List<SelectedDependencyScope>> selectedScopes = SelectedDependencyScopes.from(
                    graph,
                    selection,
                    directRequests);
            List<LockPackagePlan> packagePlans = selection.selectedNodes().stream()
                    .flatMap(node -> selectedScopes
                            .getOrDefault(node.packageId(), List.of(new SelectedDependencyScope(DependencyScope.COMPILE, false)))
                            .stream()
                            .map(scope -> lockPackagePlan(node, scope)))
                    .toList();
            Map<ArtifactDescriptor, CachedArtifact> artifacts = context.getArtifacts(
                    packagePlans.stream().map(LockPackagePlan::artifactDescriptor).toList());
            Map<PackageId, List<DependencyScope>> managedDirectScopes = managedDirectScopes(context.config());
            Map<PackageId, ManagedVersion> managedVersionDetails = context.projectManagedVersionDetails();
            List<LockPackage> packages = packagePlans.stream()
                    .map(plan -> lockPackage(
                            context,
                            plan,
                            graph,
                            artifacts.get(plan.artifactDescriptor()),
                            managedDirectScopes,
                            managedVersionDetails,
                            context.config().dependencyMetadata(),
                            graph.policyEffects()))
                    .toList();
            List<LockConflict> conflicts = selection.conflicts().stream()
                    .map(conflict -> new LockConflict(
                            conflict.packageId(),
                            conflict.selectedVersion(),
                            conflict.requests().stream().map(DependencyRequest::requestedVersion).toList(),
                            conflict.selectionReason()))
                    .toList();
            return new ZoltLockfile(
                    ZoltLockfile.CURRENT_VERSION,
                    aliasFingerprint(context.config()),
                    Optional.of(ProjectResolutionFingerprint.fingerprint(context.config())),
                    ProjectResolutionFingerprint.inputFingerprints(context.config()),
                    packages,
                    conflicts,
                    LockfilePolicyPlanner.lockPolicyEffects(graph.policyEffects()));
        } finally {
            context.addLockfileAssemblyNanos(elapsedSince(started));
        }
    }

    private static Optional<String> aliasFingerprint(ProjectConfig config) {
        List<String> inputs = new ArrayList<>();
        config.versionAliases().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "alias\t" + entry.getKey() + "\t" + entry.getValue())
                .forEach(inputs::add);
        config.dependencyMetadata().values().stream()
                .filter(metadata -> metadata.versionRef() != null)
                .sorted(Comparator
                        .comparing(DependencyMetadata::section)
                        .thenComparing(DependencyMetadata::coordinate))
                .map(metadata -> "versionRef\t"
                        + metadata.section()
                        + "\t"
                        + metadata.coordinate()
                        + "\t"
                        + metadata.versionRef()
                        + "\t"
                        + nullToEmpty(metadata.version()))
                .forEach(inputs::add);
        config.dependencyPolicy().constraints().values().stream()
                .filter(constraint -> constraint.versionRef().isPresent())
                .sorted(Comparator.comparing(DependencyConstraint::coordinate))
                .map(constraint -> "constraintVersionRef\t"
                        + constraint.coordinate()
                        + "\t"
                        + constraint.versionRef().orElseThrow()
                        + "\t"
                        + constraint.version())
                .forEach(inputs::add);
        openApiSteps(config).stream()
                .map(GeneratedSourceStep::openApi)
                .filter(settings -> settings.toolVersionRef().isPresent())
                .sorted(Comparator
                        .comparing((OpenApiGenerationSettings settings) -> settings.toolCoordinate().orElse(""))
                        .thenComparing(settings -> settings.toolVersionRef().orElseThrow()))
                .map(settings -> "openApiToolVersionRef\t"
                        + settings.toolCoordinate().orElse("")
                        + "\t"
                        + settings.toolVersionRef().orElseThrow()
                        + "\t"
                        + settings.toolVersion().orElse(""))
                .forEach(inputs::add);
        protobufSteps(config).stream()
                .map(GeneratedSourceStep::protobuf)
                .flatMap(settings -> java.util.stream.Stream.of(
                        protobufVersionRefInput("protobufProtocVersionRef", settings.protocCoordinate(), settings.protocVersionRef(), settings.protocVersion()),
                        protobufVersionRefInput("protobufGrpcPluginVersionRef", settings.grpcPluginCoordinate(), settings.grpcPluginVersionRef(), settings.grpcPluginVersion())))
                .flatMap(Optional::stream)
                .sorted()
                .forEach(inputs::add);
        if (inputs.isEmpty()) {
            return Optional.empty();
        }
        String input = String.join("\n", inputs) + "\n";
        return Optional.of("sha256:" + sha256(input.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<GeneratedSourceStep> openApiSteps(ProjectConfig config) {
        List<GeneratedSourceStep> steps = new ArrayList<>();
        config.build().generatedMainSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.OPENAPI)
                .forEach(steps::add);
        config.build().generatedTestSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.OPENAPI)
                .forEach(steps::add);
        return List.copyOf(steps);
    }

    private static List<GeneratedSourceStep> protobufSteps(ProjectConfig config) {
        List<GeneratedSourceStep> steps = new ArrayList<>();
        config.build().generatedMainSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.PROTOBUF)
                .forEach(steps::add);
        config.build().generatedTestSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.PROTOBUF)
                .forEach(steps::add);
        return List.copyOf(steps);
    }

    private static Optional<String> protobufVersionRefInput(
            String label,
            Optional<String> coordinate,
            Optional<String> versionRef,
            Optional<String> version) {
        if (versionRef.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(label
                + "\t"
                + coordinate.orElse("")
                + "\t"
                + versionRef.orElseThrow()
                + "\t"
                + version.orElse(""));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static LockPackagePlan lockPackagePlan(PackageNode node, SelectedDependencyScope selectedScope) {
        Coordinate coordinate = new Coordinate(
                node.packageId().groupId(),
                node.packageId().artifactId(),
                Optional.of(node.selectedVersion()));
        ArtifactDescriptor descriptor = selectedScope.artifactDescriptor()
                .orElseGet(() -> ArtifactDescriptor.jar(coordinate));
        return new LockPackagePlan(node, selectedScope, descriptor);
    }

    private LockPackage lockPackage(
            LockfileAssemblyContext context,
            LockPackagePlan plan,
            ResolutionGraph graph,
            CachedArtifact artifact,
            Map<PackageId, List<DependencyScope>> managedDirectScopes,
            Map<PackageId, ManagedVersion> managedVersionDetails,
            Map<String, DependencyMetadata> dependencyMetadata,
            List<DependencyPolicyEffect> policyEffects) {
        PackageNode node = plan.node();
        SelectedDependencyScope selectedScope = plan.selectedScope();
        ArtifactDescriptor descriptor = plan.artifactDescriptor();
        CachedArtifact pom = context.getPom(descriptor.coordinate());
        boolean jarArtifact = "jar".equals(descriptor.extension());
        return new LockPackage(
                node.packageId(),
                node.selectedVersion(),
                context.sourceFor(artifact),
                selectedScope.scope(),
                selectedScope.direct(),
                jarArtifact ? Optional.of(artifact.repositoryPath()) : Optional.empty(),
                Optional.of(pom.repositoryPath()),
                jarArtifact ? Optional.of(sha256(artifact.bytes())) : Optional.empty(),
                Optional.of(sha256(pom.bytes())),
                jarArtifact ? Optional.empty() : Optional.of(artifact.repositoryPath()),
                jarArtifact ? Optional.empty() : Optional.of(descriptor.extension()),
                jarArtifact ? Optional.empty() : Optional.of(sha256(artifact.bytes())),
                dependenciesFor(node, graph),
                LockfilePolicyPlanner.policiesFor(
                        node,
                        selectedScope,
                        context.config().dependencyPolicy().constraints(),
                        managedDirectScopes,
                        managedVersionDetails,
                        dependencyMetadata,
                        policyEffects));
    }

    private static List<String> dependenciesFor(PackageNode node, ResolutionGraph graph) {
        return graph.edges().stream()
                .filter(edge -> edge.from().equals(node))
                .map(edge -> edge.to().packageId() + ":" + edge.to().selectedVersion())
                .sorted()
                .toList();
    }

    private Map<PackageId, List<DependencyScope>> managedDirectScopes(ProjectConfig config) {
        Map<PackageId, List<DependencyScope>> scopes = new LinkedHashMap<>();
        addManagedDirectScopes(scopes, config.managedApiDependencies(), DependencyScope.COMPILE);
        addManagedDirectScopes(scopes, config.managedDependencies(), DependencyScope.COMPILE);
        addManagedDirectScopes(scopes, config.managedRuntimeDependencies(), DependencyScope.RUNTIME);
        addManagedDirectScopes(scopes, config.managedProvidedDependencies(), DependencyScope.PROVIDED);
        addManagedDirectScopes(scopes, config.managedDevDependencies(), DependencyScope.DEV);
        addManagedDirectScopes(scopes, config.managedTestDependencies(), DependencyScope.TEST);
        addManagedDirectScopes(scopes, config.managedAnnotationProcessors(), DependencyScope.PROCESSOR);
        addManagedDirectScopes(scopes, config.managedTestAnnotationProcessors(), DependencyScope.TEST_PROCESSOR);
        return scopes.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .distinct()
                                .sorted(Comparator.comparing(DependencyScope::lockfileName))
                                .toList(),
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    private void addManagedDirectScopes(
            Map<PackageId, List<DependencyScope>> scopes,
            Iterable<String> dependencies,
            DependencyScope scope) {
        for (String dependency : dependencies) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            scopes.computeIfAbsent(PackageId.from(coordinate), ignored -> new ArrayList<>()).add(scope);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ResolveException("Could not compute artifact checksum because SHA-256 is unavailable.", exception);
        }
    }

    private static long elapsedSince(long started) {
        return Math.max(0L, System.nanoTime() - started);
    }

    private record LockPackagePlan(
            PackageNode node,
            SelectedDependencyScope selectedScope,
            ArtifactDescriptor artifactDescriptor) {
    }
}
