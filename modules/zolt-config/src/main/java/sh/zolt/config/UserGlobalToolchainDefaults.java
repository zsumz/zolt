package sh.zolt.config;

import sh.zolt.project.toolchain.JavaToolchainRequest;
import java.util.Optional;

public record UserGlobalToolchainDefaults(Optional<JavaToolchainRequest> java) {
    public UserGlobalToolchainDefaults {
        java = java == null ? Optional.empty() : java;
    }

    public static UserGlobalToolchainDefaults none() {
        return new UserGlobalToolchainDefaults(Optional.empty());
    }
}
