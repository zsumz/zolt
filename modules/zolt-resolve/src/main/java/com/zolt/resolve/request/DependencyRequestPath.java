package com.zolt.resolve.request;

import com.zolt.dependency.PackageId;
import java.util.List;

public record DependencyRequestPath(List<PackageId> packages) {
    public DependencyRequestPath {
        packages = List.copyOf(packages);
    }
}
