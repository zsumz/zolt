package sh.zolt.toolchain.catalog;

import sh.zolt.project.toolchain.JavaToolchainRequest;
import sh.zolt.toolchain.lock.LockedJavaToolchain;
import sh.zolt.toolchain.platform.HostPlatform;
import java.util.Optional;

public interface JavaToolchainCatalog {
    Optional<LockedJavaToolchain> lock(JavaToolchainRequest request, HostPlatform platform);

    default Optional<JavaToolchainArtifact> artifact(LockedJavaToolchain locked) {
        return Optional.empty();
    }
}
