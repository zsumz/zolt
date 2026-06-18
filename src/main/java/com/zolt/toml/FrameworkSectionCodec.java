package com.zolt.toml;

import com.zolt.project.FrameworkSettings;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.project.SpringBootSettings;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlTable;

final class FrameworkSectionCodec {
    private static final Set<String> FRAMEWORK_KEYS = Set.of("springBoot", "quarkus");
    private static final Set<String> SPRING_BOOT_KEYS = Set.of("native");
    private static final Set<String> SPRING_BOOT_NATIVE_KEYS = Set.of("enabled");
    private static final Set<String> QUARKUS_KEYS = Set.of("enabled", "package");

    private FrameworkSectionCodec() {
    }

    static FrameworkSettings parse(TomlTable table) {
        if (table == null) {
            return FrameworkSettings.defaults();
        }

        TomlValidation.validateKeys("framework", table, FRAMEWORK_KEYS);
        return new FrameworkSettings(
                parseSpringBoot(table.getTable(List.of("springBoot"))),
                parseQuarkus(table.getTable(List.of("quarkus"))));
    }

    static void write(StringBuilder toml, FrameworkSettings frameworkSettings) {
        if (frameworkSettings == null || frameworkSettings.equals(FrameworkSettings.defaults())) {
            return;
        }
        SpringBootSettings springBoot = frameworkSettings.springBoot();
        if (!springBoot.equals(SpringBootSettings.defaults())) {
            toml.append("\n[framework.springBoot.native]\n");
            writeAssignment(toml, "enabled", springBoot.nativeEnabled());
        }
        QuarkusSettings quarkus = frameworkSettings.quarkus();
        if (!quarkus.equals(QuarkusSettings.defaults())) {
            toml.append("\n[framework.quarkus]\n");
            writeAssignment(toml, "enabled", quarkus.enabled());
            writeAssignment(toml, "package", quarkus.packageMode().configValue());
        }
    }

    private static SpringBootSettings parseSpringBoot(TomlTable table) {
        SpringBootSettings defaults = SpringBootSettings.defaults();
        if (table == null) {
            return defaults;
        }

        TomlValidation.validateKeys("framework.springBoot", table, SPRING_BOOT_KEYS);
        TomlTable nativeTable = table.getTable(List.of("native"));
        if (nativeTable == null) {
            return defaults;
        }
        TomlValidation.validateKeys("framework.springBoot.native", nativeTable, SPRING_BOOT_NATIVE_KEYS);
        return new SpringBootSettings(TomlScalars.booleanOrDefault(
                nativeTable,
                "framework.springBoot.native",
                "enabled",
                defaults.nativeEnabled()));
    }

    private static QuarkusSettings parseQuarkus(TomlTable table) {
        QuarkusSettings defaults = QuarkusSettings.defaults();
        if (table == null) {
            return defaults;
        }

        TomlValidation.validateKeys("framework.quarkus", table, QUARKUS_KEYS);
        boolean enabled = TomlScalars.booleanOrDefault(
                table,
                "framework.quarkus",
                "enabled",
                defaults.enabled());
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
