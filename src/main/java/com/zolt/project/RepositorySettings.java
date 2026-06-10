package com.zolt.project;

import java.util.Optional;

public record RepositorySettings(String id, String url, Optional<String> credentials) {
    public RepositorySettings {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Repository id must be non-empty.");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Repository URL must be non-empty.");
        }
        credentials = credentials == null || credentials.filter(value -> !value.isBlank()).isEmpty()
                ? Optional.empty()
                : credentials.map(String::trim);
    }

    public static RepositorySettings unauthenticated(String id, String url) {
        return new RepositorySettings(id, url, Optional.empty());
    }
}
