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
        List<String> dependencies) {
    public LockPackage {
        jar = jar == null ? Optional.empty() : jar;
        pom = pom == null ? Optional.empty() : pom;
        jarSha256 = jarSha256 == null ? Optional.empty() : jarSha256;
        pomSha256 = pomSha256 == null ? Optional.empty() : pomSha256;
        dependencies = List.copyOf(dependencies);
    }
}
