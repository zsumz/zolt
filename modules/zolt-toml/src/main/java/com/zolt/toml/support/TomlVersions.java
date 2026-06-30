package com.zolt.toml.support;

import com.zolt.project.VersionAliasRules;
import com.zolt.project.VersionPolicy;
import com.zolt.toml.ZoltConfigException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.tomlj.TomlTable;

public final class TomlVersions {
    private TomlVersions() {
    }

    public static Optional<String> optionalVersionOrRef(
            TomlTable table,
            String section,
            VersionPolicy.Context versionContext,
            Map<String, String> versionAliases) {
        Object rawVersion = table.get(List.of("version"));
        Object rawVersionRef = table.get(List.of("versionRef"));
        if (rawVersion != null && rawVersionRef != null) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "] in zolt.toml. Use either version or versionRef, not both.");
        }
        if (rawVersion == null && rawVersionRef == null) {
            return Optional.empty();
        }
        if (rawVersion instanceof String version && !version.isBlank()) {
            validateVersion(versionContext, section, version);
            return Optional.of(version);
        }
        if (rawVersion != null) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "].version in zolt.toml. Use a non-empty string version.");
        }
        return Optional.of(requiredVersionRef(table, section, versionAliases));
    }

    public static Optional<String> optionalVersionRef(
            TomlTable table,
            String section,
            Map<String, String> versionAliases) {
        Object rawVersionRef = table.get(List.of("versionRef"));
        if (rawVersionRef == null) {
            return Optional.empty();
        }
        requiredVersionRef(table, section, versionAliases);
        return Optional.of((String) rawVersionRef);
    }

    public static String requiredVersionRef(
            TomlTable table,
            String section,
            Map<String, String> versionAliases) {
        Object rawValue = table.get(List.of("versionRef"));
        if (!(rawValue instanceof String alias) || alias.isBlank()) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "].versionRef in zolt.toml. Use a non-empty alias from [versions].");
        }
        validateVersionAliasName(alias);
        String version = versionAliases.get(alias);
        if (version == null) {
            throw new ZoltConfigException(
                    "Unknown versionRef `"
                            + alias
                            + "` in ["
                            + section
                            + "]. Add [versions]."
                            + alias
                            + " or use an explicit version.");
        }
        return version;
    }

    public static void validateVersion(
            VersionPolicy.Context context,
            String subject,
            String version) {
        VersionPolicy.violation(context, version).ifPresent(violation -> {
            throw new ZoltConfigException(com.zolt.error.ActionableError.of(
                    "Invalid "
                            + context.description()
                            + " `"
                            + version
                            + "` for ["
                            + subject
                            + "] in zolt.toml.",
                    violation.guidance()));
        });
    }

    private static void validateVersionAliasName(String alias) {
        if (!VersionAliasRules.isValidName(alias)) {
            throw new ZoltConfigException(
                    "Invalid [versions] alias `"
                            + alias
                            + "`. Alias names may contain only letters, digits, dot, underscore, and hyphen.");
        }
    }
}
