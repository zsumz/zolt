package com.zolt.toml;

import com.zolt.project.NativeSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

final class NativeSectionCodec {
    private static final Set<String> NATIVE_KEYS = Set.of("imageName", "output", "args");

    private NativeSectionCodec() {
    }

    static NativeSettings parse(TomlTable table, String projectName) {
        if (table == null) {
            return NativeSettings.defaults();
        }

        NativeSettings defaults = NativeSettings.defaults().withDefaultImageName(projectName);
        TomlValidation.validateKeys("native", table, NATIVE_KEYS);
        return new NativeSettings(
                stringOrDefault(table, "native", "imageName", defaults.imageName()),
                stringOrDefault(table, "native", "output", defaults.output()),
                stringListOrDefault(table, "native", "args", defaults.args()));
    }

    static void write(StringBuilder toml, NativeSettings nativeSettings) {
        NativeSettings defaults = NativeSettings.defaults();
        if (nativeSettings == null
                || ((nativeSettings.imageName() == null || nativeSettings.imageName().isBlank())
                && nativeSettings.output().equals(defaults.output())
                && nativeSettings.args().isEmpty())) {
            return;
        }
        toml.append("\n[native]\n");
        if (nativeSettings.imageName() != null && !nativeSettings.imageName().isBlank()) {
            writeAssignment(toml, "imageName", nativeSettings.imageName());
        }
        writeAssignment(toml, "output", nativeSettings.output());
        writeStringArray(toml, "args", nativeSettings.args());
    }

    private static String stringOrDefault(TomlTable table, String section, String key, String defaultValue) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a non-empty string value.");
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
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use an array of strings.");
        }
        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            Object element = array.get(index);
            if (!(element instanceof String value) || value.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "]." + key + "[" + index + "] in zolt.toml. Use a non-empty string.");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static void writeAssignment(StringBuilder toml, String key, String value) {
        toml.append(key).append(" = ").append(quote(value)).append('\n');
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
