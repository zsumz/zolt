package com.zolt.toml;

import com.zolt.project.FrameworkSettings;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlTable;

final class FrameworkSectionCodec {
    private static final Set<String> FRAMEWORK_KEYS = Set.of("quarkus");
    private static final Set<String> QUARKUS_KEYS = Set.of("enabled", "package");

    private FrameworkSectionCodec() {
    }

    static FrameworkSettings parse(TomlTable table) {
        if (table == null) {
            return FrameworkSettings.defaults();
        }

        validateKeys("framework", table, FRAMEWORK_KEYS);
        return new FrameworkSettings(parseQuarkus(table.getTable(List.of("quarkus"))));
    }

    static void write(StringBuilder toml, FrameworkSettings frameworkSettings) {
        if (frameworkSettings == null || frameworkSettings.equals(FrameworkSettings.defaults())) {
            return;
        }
        QuarkusSettings quarkus = frameworkSettings.quarkus();
        if (!quarkus.equals(QuarkusSettings.defaults())) {
            toml.append("\n[framework.quarkus]\n");
            writeAssignment(toml, "enabled", quarkus.enabled());
            writeAssignment(toml, "package", quarkus.packageMode().configValue());
        }
    }

    private static QuarkusSettings parseQuarkus(TomlTable table) {
        QuarkusSettings defaults = QuarkusSettings.defaults();
        if (table == null) {
            return defaults;
        }

        validateKeys("framework.quarkus", table, QUARKUS_KEYS);
        boolean enabled = booleanOrDefault(table, "framework.quarkus", "enabled", defaults.enabled());
        Object rawPackage = table.get(List.of("package"));
        if (rawPackage == null) {
            return new QuarkusSettings(enabled, defaults.packageMode());
        }
        if (!(rawPackage instanceof String packageMode) || packageMode.isBlank()) {
            throw new ZoltConfigException(
                    "Invalid value for [framework.quarkus].package in zolt.toml. Use one of: "
                            + QuarkusPackageMode.supportedValues()
                            + ".");
        }
        return new QuarkusSettings(
                enabled,
                QuarkusPackageMode.fromConfigValue(packageMode).orElseThrow(() -> new ZoltConfigException(
                        "Unsupported Quarkus package mode `"
                                + packageMode
                                + "` in zolt.toml. Supported Quarkus package modes are: "
                                + QuarkusPackageMode.supportedValues()
                                + ".")));
    }

    private static void validateKeys(String section, TomlTable table, Set<String> allowedKeys) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new ZoltConfigException(
                        "Unknown field [" + section + "]." + key + " in zolt.toml. Remove it or check the spelling.");
            }
        }
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

    private static void writeAssignment(StringBuilder toml, String key, boolean value) {
        toml.append(key).append(" = ").append(value).append('\n');
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
