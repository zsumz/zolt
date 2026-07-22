package sh.zolt.publish;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PublishSettings(
        String releaseRepository,
        String snapshotRepository,
        List<String> artifacts,
        Map<String, PublishRepositorySettings> repositories,
        PublishSigningSettings signing,
        PublishCentralSettings central) {
    public PublishSettings {
        releaseRepository = normalize(releaseRepository);
        snapshotRepository = normalize(snapshotRepository);
        artifacts = artifacts == null || artifacts.isEmpty()
                ? List.of("main")
                : List.copyOf(artifacts.stream().map(PublishSettings::normalizeArtifact).toList());
        repositories = repositories == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(repositories));
        signing = signing == null ? PublishSigningSettings.disabled() : signing;
        central = central == null ? PublishCentralSettings.none() : central;
    }

    public PublishSettings(
            String releaseRepository,
            String snapshotRepository,
            List<String> artifacts,
            Map<String, PublishRepositorySettings> repositories) {
        this(
                releaseRepository,
                snapshotRepository,
                artifacts,
                repositories,
                PublishSigningSettings.disabled(),
                PublishCentralSettings.none());
    }

    public PublishSettings(
            String releaseRepository,
            String snapshotRepository,
            List<String> artifacts,
            Map<String, PublishRepositorySettings> repositories,
            PublishSigningSettings signing) {
        this(releaseRepository, snapshotRepository, artifacts, repositories, signing, PublishCentralSettings.none());
    }

    public boolean configured() {
        return !releaseRepository.isBlank()
                || !snapshotRepository.isBlank()
                || !repositories.isEmpty()
                || central.configured();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeArtifact(String value) {
        if (value == null || value.isBlank()) {
            throw new PublishException("[publish].artifacts entries must be non-empty strings.");
        }
        return value.trim();
    }
}
