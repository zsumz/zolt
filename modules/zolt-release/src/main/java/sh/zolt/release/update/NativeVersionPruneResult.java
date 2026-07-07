package sh.zolt.release.update;

import java.util.List;
import java.util.Optional;

public record NativeVersionPruneResult(
        String currentVersion,
        Optional<String> previousVersion,
        int keep,
        boolean dryRun,
        List<NativeInstalledVersion> keptVersions,
        List<NativeInstalledVersion> prunedVersions) {
    public NativeVersionPruneResult {
        previousVersion = previousVersion == null ? Optional.empty() : previousVersion;
        keptVersions = List.copyOf(keptVersions);
        prunedVersions = List.copyOf(prunedVersions);
    }
}
