package com.zolt.toml;

import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.RepositorySettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlTable;

final class RepositorySectionCodec {
    private static final Set<String> REPOSITORY_KEYS = Set.of("url", "credentials");
    private static final Set<String> REPOSITORY_CREDENTIAL_KEYS = Set.of("usernameEnv", "passwordEnv");

    private RepositorySectionCodec() {
    }

    static Map<String, RepositorySettings> repositorySettings(TomlTable table) {
        if (table == null) {
            return Map.of();
        }

        Map<String, RepositorySettings> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object rawValue = table.get(List.of(key));
            if (rawValue instanceof String value) {
                if (value.isBlank()) {
                    throw invalidRepositoryValue(key);
                }
                values.put(key, RepositorySettings.unauthenticated(key, value));
                continue;
            }
            if (rawValue instanceof TomlTable repositoryTable) {
                TomlValidation.validateKeys("repositories." + key, repositoryTable, REPOSITORY_KEYS);
                String url = TomlScalars.requiredString(repositoryTable, "repositories." + key, "url");
                Optional<String> credentials =
                        TomlScalars.optionalString(repositoryTable, "repositories." + key, "credentials");
                values.put(key, new RepositorySettings(key, url, credentials));
                continue;
            }
            throw invalidRepositoryValue(key);
        }
        return values;
    }

    static Map<String, String> repositoryUrls(Map<String, RepositorySettings> repositorySettings) {
        Map<String, String> urls = new LinkedHashMap<>();
        for (Map.Entry<String, RepositorySettings> entry : repositorySettings.entrySet()) {
            urls.put(entry.getKey(), entry.getValue().url());
        }
        return urls;
    }

    static Map<String, RepositoryCredentialSettings> repositoryCredentials(TomlTable table) {
        if (table == null) {
            return Map.of();
        }

        Map<String, RepositoryCredentialSettings> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            TomlTable credentialTable = table.getTable(List.of(key));
            if (credentialTable == null) {
                throw new ZoltConfigException(
                        "Invalid value for [repositoryCredentials]." + key + " in zolt.toml. Use a table with usernameEnv and passwordEnv.");
            }
            TomlValidation.validateKeys("repositoryCredentials." + key, credentialTable, REPOSITORY_CREDENTIAL_KEYS);
            values.put(key, new RepositoryCredentialSettings(
                    key,
                    TomlScalars.requiredString(credentialTable, "repositoryCredentials." + key, "usernameEnv"),
                    TomlScalars.requiredString(credentialTable, "repositoryCredentials." + key, "passwordEnv")));
        }
        return values;
    }

    static void validateRepositoryCredentialReferences(
            Map<String, RepositorySettings> repositories,
            Map<String, RepositoryCredentialSettings> credentials) {
        for (RepositorySettings repository : repositories.values()) {
            Optional<String> credentialId = repository.credentials();
            if (credentialId.isPresent() && !credentials.containsKey(credentialId.orElseThrow())) {
                throw new ZoltConfigException(
                        "Repository `"
                                + repository.id()
                                + "` references credentials `"
                                + credentialId.orElseThrow()
                                + "`, but [repositoryCredentials."
                                + credentialId.orElseThrow()
                                + "] is not defined.");
            }
        }
    }

    static void writeRepositories(StringBuilder toml, Map<String, RepositorySettings> repositories) {
        toml.append("[repositories]\n");
        for (Map.Entry<String, RepositorySettings> entry : new TreeMap<>(repositories).entrySet()) {
            RepositorySettings repository = entry.getValue();
            toml.append(quote(entry.getKey())).append(" = ");
            if (repository.credentials().isEmpty()) {
                toml.append(quote(repository.url())).append('\n');
                continue;
            }
            toml.append("{ url = ")
                    .append(quote(repository.url()))
                    .append(", credentials = ")
                    .append(quote(repository.credentials().orElseThrow()))
                    .append(" }\n");
        }
        toml.append('\n');
    }

    static void writeRepositoryCredentials(
            StringBuilder toml,
            Map<String, RepositoryCredentialSettings> credentials) {
        for (RepositoryCredentialSettings credential : new TreeMap<>(credentials).values()) {
            toml.append("[repositoryCredentials.")
                    .append(quote(credential.id()))
                    .append("]\n");
            writeAssignment(toml, "usernameEnv", credential.usernameEnv());
            writeAssignment(toml, "passwordEnv", credential.passwordEnv());
            toml.append('\n');
        }
    }

    private static ZoltConfigException invalidRepositoryValue(String key) {
        return new ZoltConfigException(
                "Invalid value for [repositories]." + key + " in zolt.toml. Use a non-empty URL string or { url = \"...\", credentials = \"...\" }.");
    }

    private static void writeAssignment(StringBuilder toml, String key, String value) {
        toml.append(key).append(" = ").append(quote(value)).append('\n');
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
