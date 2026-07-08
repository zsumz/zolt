package sh.zolt.toolchain;

import sh.zolt.project.toolchain.JavaToolchainRequest;
import sh.zolt.toolchain.jvm.ResolvedJavaToolchain;

public record JavaToolchainStatus(
        JavaToolchainRequest request,
        String requestSource,
        ResolvedJavaToolchain resolved) {
    private static final String PROJECT_TOOLCHAIN_SOURCE = "[toolchain.java]";

    public boolean ok() {
        return resolved.ok();
    }

    public boolean projectToolchainConfigured() {
        return PROJECT_TOOLCHAIN_SOURCE.equals(requestSource);
    }
}
