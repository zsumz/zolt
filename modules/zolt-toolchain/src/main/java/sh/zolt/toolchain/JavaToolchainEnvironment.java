package sh.zolt.toolchain;

import sh.zolt.toolchain.jvm.ResolvedJavaToolchain;
import java.nio.file.Path;

public record JavaToolchainEnvironment(
        Path javaHome,
        Path bin,
        ResolvedJavaToolchain resolved) {
    public JavaToolchainEnvironment {
        if (javaHome == null) {
            throw new IllegalArgumentException("Java toolchain environment requires javaHome.");
        }
        if (bin == null) {
            bin = javaHome.resolve("bin").normalize();
        }
        if (resolved == null) {
            throw new IllegalArgumentException("Java toolchain environment requires resolved toolchain details.");
        }
    }
}
