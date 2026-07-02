package sh.zolt.publish;

import java.util.Optional;

public record PublishRepositorySettings(String id, String url, Optional<String> credentials) {
    public PublishRepositorySettings {
        if (id == null || id.isBlank()) {
            throw new PublishException("Publish repository id must be non-empty.");
        }
        id = id.trim();
        if (url == null || url.isBlank()) {
            throw new PublishException("Publish repository `" + id + "` must define a non-empty url.");
        }
        url = url.trim();
        credentials = credentials == null || credentials.filter(value -> !value.isBlank()).isEmpty()
                ? Optional.empty()
                : credentials.map(String::trim);
    }
}
