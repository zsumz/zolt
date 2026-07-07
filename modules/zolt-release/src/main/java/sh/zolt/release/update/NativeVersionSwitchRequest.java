package sh.zolt.release.update;

import java.nio.file.Path;

public record NativeVersionSwitchRequest(
        Path installRoot,
        Path currentExecutable,
        String version) {
}
