package sh.zolt.publish;

import sh.zolt.project.RepositoryCredentialSettings;
import sh.zolt.toml.ZoltConfigException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class PublishSettingsReader {
    private static final Set<String> PUBLISH_KEYS = Set.of(
            "releaseRepository",
            "snapshotRepository",
            "artifacts",
            "repositories",
            "signing");
    private static final Set<String> PUBLISH_REPOSITORY_KEYS = Set.of("url", "credentials");
    private static final Set<String> PUBLISH_SIGNING_KEYS = Set.of("enabled", "keyId", "passphraseEnv");

    public PublishSettings read(Path zoltToml, Map<String, RepositoryCredentialSettings> credentialSettings) {
        try {
            return read(Toml.parse(zoltToml), credentialSettings);
        } catch (IOException exception) {
            throw new ZoltConfigException(
                    "Could not read zolt.toml at " + zoltToml + ". Check that the file exists and is readable.");
        }
    }

    PublishSettings read(String content, Map<String, RepositoryCredentialSettings> credentialSettings) {
        return read(Toml.parse(content), credentialSettings);
    }

    private PublishSettings read(
            TomlParseResult result,
            Map<String, RepositoryCredentialSettings> credentialSettings) {
        if (result.hasErrors()) {
            throw new ZoltConfigException(parseErrorMessage(result));
        }
        TomlTable publishTable = result.getTable("publish");
        if (publishTable == null) {
            return new PublishSettings("", "", List.of(), Map.of());
        }
        validateKeys("publish", publishTable, PUBLISH_KEYS);
        PublishSettings settings = new PublishSettings(
                optionalString(publishTable, "publish", "releaseRepository"),
                optionalString(publishTable, "publish", "snapshotRepository"),
                stringListOrDefault(publishTable, "publish", "artifacts", List.of("main")),
                publishRepositories(optionalTable(publishTable, "repositories")),
                parseSigning(optionalTable(publishTable, "signing")));
        validatePublishRepositoryReference("releaseRepository", settings.releaseRepository(), settings);
        validatePublishRepositoryReference("snapshotRepository", settings.snapshotRepository(), settings);
        validatePublishCredentialReferences(settings, credentialSettings);
        return settings;
    }

    private static PublishSigningSettings parseSigning(TomlTable table) {
        if (table == null) {
            return PublishSigningSettings.disabled();
        }
        validateKeys("publish.signing", table, PUBLISH_SIGNING_KEYS);
        return new PublishSigningSettings(
                booleanOrDefault(table, "publish.signing", "enabled", false),
                optionalStringValue(table, "publish.signing", "keyId"),
                optionalStringValue(table, "publish.signing", "passphraseEnv"));
    }

    private static boolean booleanOrDefault(TomlTable table, String section, String key, boolean defaultValue) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof Boolean value)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use true or false.");
        }
        return value;
    }

    private static Map<String, PublishRepositorySettings> publishRepositories(TomlTable table) {
        if (table == null) {
            return Map.of();
        }
        Map<String, PublishRepositorySettings> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            TomlTable repositoryTable = table.getTable(List.of(key));
            if (repositoryTable == null) {
                throw new ZoltConfigException(
                        "Invalid value for [publish.repositories]." + key + " in zolt.toml. Use { url = \"...\", credentials = \"...\" }.");
            }
            validateKeys("publish.repositories." + key, repositoryTable, PUBLISH_REPOSITORY_KEYS);
            values.put(key, new PublishRepositorySettings(
                    key,
                    requiredString(repositoryTable, "publish.repositories." + key, "url"),
                    optionalStringValue(repositoryTable, "publish.repositories." + key, "credentials")));
        }
        return values;
    }

    private static void validatePublishRepositoryReference(
            String field,
            String repositoryId,
            PublishSettings settings) {
        if (repositoryId.isBlank()) {
            return;
        }
        if (!settings.repositories().containsKey(repositoryId)) {
            throw new ZoltConfigException(
                    "[publish]."
                            + field
                            + " references publish repository `"
                            + repositoryId
                            + "`, but [publish.repositories."
                            + repositoryId
                            + "] is not defined.");
        }
    }

    private static void validatePublishCredentialReferences(
            PublishSettings settings,
            Map<String, RepositoryCredentialSettings> credentialSettings) {
        for (PublishRepositorySettings repository : settings.repositories().values()) {
            Optional<String> credentialId = repository.credentials();
            if (credentialId.isPresent() && !credentialSettings.containsKey(credentialId.orElseThrow())) {
                throw new ZoltConfigException(
                        "Publish repository `"
                                + repository.id()
                                + "` references credentials `"
                                + credentialId.orElseThrow()
                                + "`, but [repositoryCredentials."
                                + credentialId.orElseThrow()
                                + "] is not defined.");
            }
        }
    }

    private static TomlTable optionalTable(TomlTable table, String name) {
        return table == null ? null : table.getTable(List.of(name));
    }

    private static String optionalString(TomlTable table, String section, String key) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return "";
        }
        if (!(rawValue instanceof String value)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a string value.");
        }
        return value.trim();
    }

    private static Optional<String> optionalStringValue(TomlTable table, String section, String key) {
        String value = optionalString(table, section, key);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static String requiredString(TomlTable table, String section, String key) {
        String value = optionalString(table, section, key);
        if (value.isBlank()) {
            throw new ZoltConfigException(
                    "Missing required [" + section + "]." + key + " in zolt.toml.");
        }
        return value;
    }

    private static List<String> stringListOrDefault(
            TomlTable table,
            String section,
            String key,
            List<String> defaultValue) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof TomlArray array)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a string array.");
        }
        return array.toList().stream()
                .map(value -> {
                    if (!(value instanceof String string) || string.isBlank()) {
                        throw new ZoltConfigException(
                                "Invalid value for [" + section + "]." + key + " in zolt.toml. Use non-empty strings.");
                    }
                    return string.trim();
                })
                .toList();
    }

    private static void validateKeys(String section, TomlTable table, Set<String> allowedKeys) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new ZoltConfigException(
                        "Unknown key `" + key + "` in [" + section + "] in zolt.toml.");
            }
        }
    }

    private static String parseErrorMessage(TomlParseResult result) {
        TomlParseError error = result.errors().getFirst();
        return "Invalid zolt.toml: " + error.toString();
    }
}
