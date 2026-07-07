package sh.zolt.release.update;

import java.nio.file.Path;

public record NativeVersionSwitchResult(
        String previousVersion,
        String currentVersion,
        boolean switched,
        Path executable) {
}
