package sh.zolt.release.update;

import java.nio.file.Path;

public record NativeInstalledVersion(
        String version,
        boolean current,
        Path executable) {
}
