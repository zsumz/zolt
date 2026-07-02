package sh.zolt.cli.command.dependency;

import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.VersionAliasRules;
import sh.zolt.project.VersionPolicy;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        Set<String> references = new LinkedHashSet<>();
        for (DependencyMetadata metadata : config.dependencyMetadata().values()) {
            if (alias.equals(metadata.versionRef())) {
                references.add("[" + metadata.section() + "]." + metadata.coordinate());
            }
        }
        config.dependencyPolicy().constraints().values().stream()
                .filter(constraint -> constraint.versionRef().filter(alias::equals).isPresent())
                .map(constraint -> "[dependencyConstraints]." + constraint.coordinate())
                .forEach(references::add);
        config.build().generatedMainSources().stream()
                .flatMap(step -> step.openApi().toolVersionRef().stream())
                .filter(alias::equals)
                .findAny()
                .ifPresent(ignored -> references.add("[generated.openapiTool].versionRef"));
        config.build().generatedTestSources().stream()
                .flatMap(step -> step.openApi().toolVersionRef().stream())
                .filter(alias::equals)
                .findAny()
                .ifPresent(ignored -> references.add("[generated.openapiTool].versionRef"));
        return List.copyOf(references);
    }

    static final class VersionAliasCommandException extends RuntimeException {
        VersionAliasCommandException(String message) {
            super(message);
        }
    }
}
