package sh.zolt.toml;

import sh.zolt.toml.support.TomlValidation;
import sh.zolt.toml.support.TomlScalars;
import sh.zolt.project.PackageMode;
import sh.zolt.project.PackageSettings;
import sh.zolt.project.PublicationMetadata;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlTable;

final class PackageSectionCodec {
    private static final Set<String> PACKAGE_KEYS = Set.of("mode", "sources", "javadoc", "tests", "metadata", "manifest");
    private static final Set<String> PACKAGE_METADATA_KEYS = Set.of(
            "name",
            "description",
            "url",
            "license",
            "developers",
            "scm",
            "issues");

    private PackageSectionCodec() {
    }

    static PackageSettings parse(TomlTable table) {
        if (table == null) {
            return PackageSettings.defaults();
        }

        TomlValidation.validateKeysWithVersionRefHint("package", table, PACKAGE_KEYS);
        PackageSettings defaults = PackageSettings.defaults();
        PublicationMetadata metadata = parsePublicationMetadata(table.getTable(List.of("metadata")));
        Map<String, String> manifestAttributes =
                TomlScalars.stringMap(table.getTable(List.of("manifest")), "package.manifest");
        Object rawMode = table.get(List.of("mode"));
        PackageMode mode = defaults.mode();
        if (rawMode != null) {
            if (!(rawMode instanceof String modeValue) || modeValue.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid value for [package].mode in zolt.toml. Use one of: "
                                + PackageMode.supportedValues()
                                + ".");
            }
            mode = PackageMode.fromConfigValue(modeValue).orElseThrow(() -> new ZoltConfigException(
                    "Unsupported package mode `"
                            + modeValue
                            + "` in zolt.toml. Supported package modes are: "
                            + PackageMode.supportedValues()
                            + "."));
        }
        return new PackageSettings(
                mode,
                TomlScalars.booleanOrDefault(table, "package", "sources", defaults.sources()),
                TomlScalars.booleanOrDefault(table, "package", "javadoc", defaults.javadoc()),
                TomlScalars.booleanOrDefault(table, "package", "tests", defaults.tests()),
                metadata,
                manifestAttributes);
    }

    static void write(StringBuilder toml, PackageSettings settings) {
        if (settings == null || settings.equals(PackageSettings.defaults())) {
            return;
        }
        toml.append("\n[package]\n");
        if (settings.mode() != PackageMode.THIN) {
            writeAssignment(toml, "mode", settings.mode().configValue());
        }
        if (settings.sources()) {
            writeAssignment(toml, "sources", true);
        }
        if (settings.javadoc()) {
            writeAssignment(toml, "javadoc", true);
        }
        if (settings.tests()) {
            writeAssignment(toml, "tests", true);
        }
        writePublicationMetadata(toml, settings.metadata());
        writeManifestAttributes(toml, settings.manifestAttributes());
    }

    private static PublicationMetadata parsePublicationMetadata(TomlTable table) {
        if (table == null) {
            return PublicationMetadata.empty();
        }
        TomlValidation.validateKeysWithVersionRefHint("package.metadata", table, PACKAGE_METADATA_KEYS);
        PublicationMetadata defaults = PublicationMetadata.empty();
        return new PublicationMetadata(
                TomlScalars.stringOrDefault(table, "package.metadata", "name", defaults.name()),
                TomlScalars.stringOrDefault(table, "package.metadata", "description", defaults.description()),
                TomlScalars.stringOrDefault(table, "package.metadata", "url", defaults.url()),
                TomlScalars.stringOrDefault(table, "package.metadata", "license", defaults.license()),
                TomlScalars.stringListOrDefault(table, "package.metadata", "developers", defaults.developers()),
                TomlScalars.stringOrDefault(table, "package.metadata", "scm", defaults.scm()),
                TomlScalars.stringOrDefault(table, "package.metadata", "issues", defaults.issues()));
    }

    private static void writePublicationMetadata(StringBuilder toml, PublicationMetadata metadata) {
        if (metadata == null || metadata.emptyMetadata()) {
            return;
        }
        toml.append("\n[package.metadata]\n");
        if (!metadata.name().isBlank()) {
            writeAssignment(toml, "name", metadata.name());
        }
        if (!metadata.description().isBlank()) {
            writeAssignment(toml, "description", metadata.description());
        }
        if (!metadata.url().isBlank()) {
            writeAssignment(toml, "url", metadata.url());
        }
        if (!metadata.license().isBlank()) {
            writeAssignment(toml, "license", metadata.license());
        }
        if (!metadata.developers().isEmpty()) {
            writeStringArray(toml, "developers", metadata.developers());
        }
        if (!metadata.scm().isBlank()) {
            writeAssignment(toml, "scm", metadata.scm());
        }
        if (!metadata.issues().isBlank()) {
            writeAssignment(toml, "issues", metadata.issues());
        }
    }

    private static void writeManifestAttributes(StringBuilder toml, Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        if (toml.length() > 1 && toml.charAt(toml.length() - 1) == '\n' && toml.charAt(toml.length() - 2) != '\n') {
            toml.append('\n');
        }
        writeStringMap(toml, "package.manifest", attributes);
    }

    private static void writeAssignment(StringBuilder toml, String key, boolean value) {
        toml.append(key).append(" = ").append(value).append('\n');
    }

    private static void writeAssignment(StringBuilder toml, String key, String value) {
        toml.append(key).append(" = ").append(quote(value)).append('\n');
    }

    private static void writeStringMap(StringBuilder toml, String section, Map<String, String> values) {
        toml.append('[').append(section).append("]\n");
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            toml.append(quote(entry.getKey())).append(" = ").append(quote(entry.getValue())).append('\n');
        }
        toml.append('\n');
    }

    private static void writeStringArray(StringBuilder toml, String key, List<String> values) {
        toml.append(key).append(" = [");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                toml.append(", ");
            }
            toml.append(quote(values.get(index)));
        }
        toml.append("]\n");
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
