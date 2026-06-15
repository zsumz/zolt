package com.zolt.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ProjectConfigNormalizer {
    private ProjectConfigNormalizer() {
    }

    static Map<String, String> defaultRepositories() {
        return Map.of("central", ProjectConfig.MAVEN_CENTRAL);
    }

    static Map<String, RepositorySettings> defaultRepositorySettings() {
        return repositorySettingsFromUrls(defaultRepositories());
    }

    static Map<String, String> orderedMap(Map<String, String> values) {
        if (values == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    static Map<String, RepositorySettings> orderedRepositorySettings(Map<String, RepositorySettings> values) {
        if (values == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    static Map<String, RepositoryCredentialSettings> orderedRepositoryCredentials(
            Map<String, RepositoryCredentialSettings> values) {
        if (values == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    static Map<String, RepositorySettings> repositorySettingsFromUrls(Map<String, String> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            return Map.of();
        }
        Map<String, RepositorySettings> settings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : repositories.entrySet()) {
            settings.put(entry.getKey(), new RepositorySettings(entry.getKey(), entry.getValue(), Optional.empty()));
        }
        return Collections.unmodifiableMap(settings);
    }

    static Map<String, String> repositoryUrls(Map<String, RepositorySettings> repositorySettings) {
        if (repositorySettings == null || repositorySettings.isEmpty()) {
            return Map.of();
        }
        Map<String, String> urls = new LinkedHashMap<>();
        for (Map.Entry<String, RepositorySettings> entry : repositorySettings.entrySet()) {
            urls.put(entry.getKey(), entry.getValue().url());
        }
        return Collections.unmodifiableMap(urls);
    }

    static Set<String> orderedSet(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    static Map<String, DependencyMetadata> orderedMetadataMap(Map<String, DependencyMetadata> values) {
        if (values == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
