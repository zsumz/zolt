package sh.zolt.release.update;

import java.nio.file.Path;

public record NativeVersionListRequest(
        Path installRoot,
        Path currentExecutable) {
}
