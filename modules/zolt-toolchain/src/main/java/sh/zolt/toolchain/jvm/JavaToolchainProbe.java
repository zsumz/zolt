package sh.zolt.toolchain.jvm;

import sh.zolt.project.toolchain.JavaToolchainRequest;

@FunctionalInterface
public interface JavaToolchainProbe {
    ResolvedJavaToolchain resolve(JavaToolchainRequest request);
}
