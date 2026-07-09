package sh.zolt.toolchain;

import sh.zolt.toolchain.lock.LockedJavaToolchain;
import java.nio.file.Path;

public record ToolchainSyncResult(
        Path lockfile,
        LockedJavaToolchain locked,
        Path installPath,
        boolean installed,
        boolean downloaded) {
}
