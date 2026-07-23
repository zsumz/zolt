package sh.zolt.cli.command.dependency;

import sh.zolt.project.ProjectConfig;
import sh.zolt.project.VersionAliasRules;
import sh.zolt.project.VersionPolicy;
import sh.zolt.update.AliasReferences;
import java.util.List;

final class VersionAliasCommands {
    private VersionAliasCommands() {
    }

    static String validateAlias(String alias) {
        if (alias == null || alias.isBlank() || !alias.equals(alias.trim())) {
            throw new VersionAliasCommandException(
                    "Version alias must be non-empty and must not contain leading or trailing whitespace.");
        }
        if (!VersionAliasRules.isValidName(alias)) {
            throw new VersionAliasCommandException(
                    "Invalid version alias `"
                            + alias
                            + "`. Alias names may contain only letters, digits, dot, underscore, and hyphen.");
        }
        return alias;
    }

    static String validateValue(String alias, String version) {
        VersionPolicy.violation(VersionPolicy.Context.VERSION_ALIAS, version).ifPresent(violation -> {
            throw new VersionAliasCommandException(
                    "Invalid "
                            + VersionPolicy.Context.VERSION_ALIAS.description()
                            + " `"
                            + version
                            + "` for [versions]."
                            + alias
                            + ". "
                            + violation.guidance());
        });
        return version;
    }

    static List<String> references(ProjectConfig config, String alias) {
        return AliasReferences.referencingLabels(config, alias);
    }

    static final class VersionAliasCommandException extends RuntimeException {
        VersionAliasCommandException(String message) {
            super(message);
        }
    }
}
