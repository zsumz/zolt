package sh.zolt.release.update;

import java.util.List;

public record NativeVersionListResult(
        String currentVersion,
        List<NativeInstalledVersion> versions) {
    public NativeVersionListResult {
        versions = List.copyOf(versions);
    }
}
