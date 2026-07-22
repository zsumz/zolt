package sh.zolt.config;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Machine-level network settings from {@code [network]} in the user global config: a PEM CA bundle
 * to trust in addition to the JDK defaults, and an internal mirror base URL for Java toolchain
 * downloads. These are transport settings, not build inputs, so they never affect zolt.lock.
 */
public record NetworkConfig(Optional<Path> caBundle, Optional<String> toolchainMirror) {
    public NetworkConfig {
        caBundle = caBundle == null ? Optional.empty() : caBundle;
        toolchainMirror = toolchainMirror == null ? Optional.empty() : toolchainMirror;
    }

    public static NetworkConfig none() {
        return new NetworkConfig(Optional.empty(), Optional.empty());
    }
}
