package sh.zolt.provenance;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@FunctionalInterface
public interface BuildProvenanceSource {
    BuildProvenance read(Path projectRoot, Map<String, String> environment, Clock clock);

    static BuildProvenanceSource empty() {
        return system("", path -> Optional.empty());
    }

    static BuildProvenanceSource system(
            String zoltVersion,
            Function<Path, Optional<String>> resolutionFingerprintReader) {
        return (projectRoot, environment, clock) -> new BuildProvenanceReader().read(
                projectRoot,
                zoltVersion == null ? "" : zoltVersion,
                resolutionFingerprint(projectRoot, resolutionFingerprintReader),
                environment,
                clock);
    }

    private static Optional<String> resolutionFingerprint(
            Path projectRoot,
            Function<Path, Optional<String>> resolutionFingerprintReader) {
        if (resolutionFingerprintReader == null) {
            return Optional.empty();
        }
        Optional<String> fingerprint = resolutionFingerprintReader.apply(projectRoot);
        return fingerprint == null ? Optional.empty() : fingerprint;
    }
}
