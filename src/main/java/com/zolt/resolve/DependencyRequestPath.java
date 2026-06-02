package com.zolt.resolve;

import java.util.List;

public record DependencyRequestPath(List<PackageId> packages) {
    public DependencyRequestPath {
        packages = List.copyOf(packages);
    }
}
