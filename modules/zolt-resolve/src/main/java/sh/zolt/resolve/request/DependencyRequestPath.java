package sh.zolt.resolve.request;

import sh.zolt.dependency.PackageId;
import java.util.List;

public record DependencyRequestPath(List<PackageId> packages) {
    public DependencyRequestPath {
        packages = List.copyOf(packages);
    }
}
