package com.zolt.lockfile;

import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
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
        Optional<String> workspace,
        Optional<String> workspaceOutput,
        List<String> dependencies,
        List<String> members,
        List<String> exportedBy) {
    public LockPackage {
        jar = jar == null ? Optional.empty() : jar;
        pom = pom == null ? Optional.empty() : pom;
        jarSha256 = jarSha256 == null ? Optional.empty() : jarSha256;
        pomSha256 = pomSha256 == null ? Optional.empty() : pomSha256;
        workspace = workspace == null ? Optional.empty() : workspace;
        workspaceOutput = workspaceOutput == null ? Optional.empty() : workspaceOutput;
        dependencies = List.copyOf(dependencies);
        members = members == null ? List.of() : List.copyOf(members);
        exportedBy = exportedBy == null ? List.of() : List.copyOf(exportedBy);
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
                dependencies,
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
                workspace,
                workspaceOutput,
                dependencies,
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
                workspace,
                workspaceOutput,
                dependencies,
                members,
                List.of());
    }
}
