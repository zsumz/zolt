package sh.zolt.lockfile;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import java.util.List;
import java.util.Optional;

public record LockPackage(
        PackageId packageId,
        String version,
        String source,
        DependencyScope scope,
        boolean direct,
        Optional<String> jar,
        Optional<String> pom,
        Optional<String> jarSha256,
        Optional<String> pomSha256,
        Optional<String> artifact,
        Optional<String> artifactType,
        Optional<String> artifactSha256,
        Optional<String> workspace,
        Optional<String> workspaceOutput,
        List<String> dependencies,
        List<String> members,
        List<String> exportedBy,
        List<String> policies,
        List<String> toolGroups) {
    public LockPackage {
        jar = jar == null ? Optional.empty() : jar;
        pom = pom == null ? Optional.empty() : pom;
        jarSha256 = jarSha256 == null ? Optional.empty() : jarSha256;
        pomSha256 = pomSha256 == null ? Optional.empty() : pomSha256;
        artifact = artifact == null ? Optional.empty() : artifact;
        artifactType = artifactType == null ? Optional.empty() : artifactType;
        artifactSha256 = artifactSha256 == null ? Optional.empty() : artifactSha256;
        workspace = workspace == null ? Optional.empty() : workspace;
        workspaceOutput = workspaceOutput == null ? Optional.empty() : workspaceOutput;
        dependencies = List.copyOf(dependencies);
        members = members == null ? List.of() : List.copyOf(members);
        exportedBy = exportedBy == null ? List.of() : List.copyOf(exportedBy);
        policies = policies == null ? List.of() : List.copyOf(policies);
        toolGroups = toolGroups == null ? List.of() : List.copyOf(toolGroups);
    }

    /**
     * A copy of this package tagged with the named exec tool groups whose locked closure it belongs to.
     * The qualifier is additive: a package may serve several tools (the list unions), while two tools
     * that need different versions of the same GA stay separate entries because their {@code version}
     * differs. Empty {@code toolGroups} on a {@code tool-exec} entry means the lock predates per-tool
     * isolation and must be re-resolved before its classpath can be trusted.
     */
    public LockPackage withToolGroups(List<String> toolGroups) {
        return new LockPackage(
                packageId,
                version,
                source,
                scope,
                direct,
                jar,
                pom,
                jarSha256,
                pomSha256,
                artifact,
                artifactType,
                artifactSha256,
                workspace,
                workspaceOutput,
                dependencies,
                members,
                exportedBy,
                policies,
                toolGroups);
    }

    /**
     * Backwards-compatible constructor matching the pre-toolGroups canonical shape; {@code toolGroups}
     * defaults to empty. Callers that lock exec tooling attach groups via {@link #withToolGroups}.
     */
    public LockPackage(
            PackageId packageId,
            String version,
            String source,
            DependencyScope scope,
            boolean direct,
            Optional<String> jar,
            Optional<String> pom,
            Optional<String> jarSha256,
            Optional<String> pomSha256,
            Optional<String> artifact,
            Optional<String> artifactType,
            Optional<String> artifactSha256,
            Optional<String> workspace,
            Optional<String> workspaceOutput,
            List<String> dependencies,
            List<String> members,
            List<String> exportedBy,
            List<String> policies) {
        this(
                packageId,
                version,
                source,
                scope,
                direct,
                jar,
                pom,
                jarSha256,
                pomSha256,
                artifact,
                artifactType,
                artifactSha256,
                workspace,
                workspaceOutput,
                dependencies,
                members,
                exportedBy,
                policies,
                List.of());
    }

    public LockPackage(
            PackageId packageId,
            String version,
            String source,
            DependencyScope scope,
            boolean direct,
            Optional<String> jar,
            Optional<String> pom,
            Optional<String> jarSha256,
            Optional<String> pomSha256,
            List<String> dependencies) {
        this(
                packageId,
                version,
                source,
                scope,
                direct,
                jar,
                pom,
                jarSha256,
                pomSha256,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                dependencies,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    public LockPackage(
            PackageId packageId,
            String version,
            String source,
            DependencyScope scope,
            boolean direct,
            Optional<String> jar,
            Optional<String> pom,
            Optional<String> jarSha256,
            Optional<String> pomSha256,
            Optional<String> artifact,
            Optional<String> artifactType,
            Optional<String> artifactSha256,
            Optional<String> workspace,
            Optional<String> workspaceOutput,
            List<String> dependencies,
            List<String> members,
            List<String> exportedBy) {
        this(
                packageId,
                version,
                source,
                scope,
                direct,
                jar,
                pom,
                jarSha256,
                pomSha256,
                artifact,
                artifactType,
                artifactSha256,
                workspace,
                workspaceOutput,
                dependencies,
                members,
                exportedBy,
                List.of(),
                List.of());
    }

    public LockPackage(
            PackageId packageId,
            String version,
            String source,
            DependencyScope scope,
            boolean direct,
            Optional<String> jar,
            Optional<String> pom,
            Optional<String> jarSha256,
            Optional<String> pomSha256,
            Optional<String> artifact,
            Optional<String> artifactType,
            Optional<String> artifactSha256,
            List<String> dependencies,
            List<String> policies) {
        this(
                packageId,
                version,
                source,
                scope,
                direct,
                jar,
                pom,
                jarSha256,
                pomSha256,
                artifact,
                artifactType,
                artifactSha256,
                Optional.empty(),
                Optional.empty(),
                dependencies,
                List.of(),
                List.of(),
                policies,
                List.of());
    }

    public LockPackage(
            PackageId packageId,
            String version,
            String source,
            DependencyScope scope,
            boolean direct,
            Optional<String> jar,
            Optional<String> pom,
            Optional<String> jarSha256,
            Optional<String> pomSha256,
            Optional<String> artifact,
            Optional<String> artifactType,
            Optional<String> artifactSha256,
            List<String> dependencies) {
        this(
                packageId,
                version,
                source,
                scope,
                direct,
                jar,
                pom,
                jarSha256,
                pomSha256,
                artifact,
                artifactType,
                artifactSha256,
                Optional.empty(),
                Optional.empty(),
                dependencies,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    public LockPackage(
            PackageId packageId,
            String version,
            String source,
            DependencyScope scope,
            boolean direct,
            Optional<String> jar,
            Optional<String> pom,
            Optional<String> jarSha256,
            Optional<String> pomSha256,
            Optional<String> workspace,
            Optional<String> workspaceOutput,
            List<String> dependencies) {
        this(
                packageId,
                version,
                source,
                scope,
                direct,
                jar,
                pom,
                jarSha256,
                pomSha256,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                workspace,
                workspaceOutput,
                dependencies,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    public LockPackage(
            PackageId packageId,
            String version,
            String source,
            DependencyScope scope,
            boolean direct,
            Optional<String> jar,
            Optional<String> pom,
            Optional<String> jarSha256,
            Optional<String> pomSha256,
            Optional<String> workspace,
            Optional<String> workspaceOutput,
            List<String> dependencies,
            List<String> members) {
        this(
                packageId,
                version,
                source,
                scope,
                direct,
                jar,
                pom,
                jarSha256,
                pomSha256,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                workspace,
                workspaceOutput,
                dependencies,
                members,
                List.of(),
                List.of(),
                List.of());
    }

    public LockPackage(
            PackageId packageId,
            String version,
            String source,
            DependencyScope scope,
            boolean direct,
            Optional<String> jar,
            Optional<String> pom,
            Optional<String> jarSha256,
            Optional<String> pomSha256,
            Optional<String> workspace,
            Optional<String> workspaceOutput,
            List<String> dependencies,
            List<String> members,
            List<String> exportedBy) {
        this(
                packageId,
                version,
                source,
                scope,
                direct,
                jar,
                pom,
                jarSha256,
                pomSha256,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                workspace,
                workspaceOutput,
                dependencies,
                members,
                exportedBy,
                List.of(),
                List.of());
    }
}
