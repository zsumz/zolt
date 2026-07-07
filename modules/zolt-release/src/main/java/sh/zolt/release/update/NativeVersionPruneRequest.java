package sh.zolt.release.update;

import java.nio.file.Path;

public record NativeVersionPruneRequest(
        Path installRoot,
        Path currentExecutable,
        int keep,
        boolean dryRun) {
}
