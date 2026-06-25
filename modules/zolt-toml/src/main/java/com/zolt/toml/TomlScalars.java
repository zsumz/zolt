package com.zolt.toml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

final class TomlScalars {
    private TomlScalars() {
    }

    static String requiredString(TomlTable table, String section, String key) {
        String value = table.getString(key);
        if (value == null || value.isBlank()) {
            throw new ZoltConfigException(
                    "Missing required field [" + section + "]." + key + " in zolt.toml. Add a non-empty string value.");
        }
        return value;
    }

    static Optional<String> optionalString(TomlTable table, String section, String key) {
        String value = table.getString(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    static String stringOrDefault(TomlTable table, String section, String key, String defaultValue) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof String value)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a non-empty string value.");
        }
        if (value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    static String nonBlankStringOrDefault(TomlTable table, String section, String key, String defaultValue) {
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

    static boolean booleanOrDefault(TomlTable table, String section, String key, boolean defaultValue) {
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

    static Optional<Boolean> optionalBoolean(TomlTable table, String section, String key) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return Optional.empty();
        }
        if (!(rawValue instanceof Boolean value)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use true or false.");
        }
        return Optional.of(value);
    }

    static int integerOrDefault(TomlTable table, String section, String key, int defaultValue) {
        Object rawValue = table.get(List.of(key));
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof Long value)) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use an integer.");
        }
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException exception) {
            throw new ZoltConfigException(
                    "Invalid value for [" + section + "]." + key + " in zolt.toml. Use an integer within Java's int range.");
        }
    }

    static Map<String, String> stringMap(TomlTable table, String section) {
        if (table == null) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object rawValue = table.get(List.of(key));
            if (!(rawValue instanceof String value) || value.isBlank()) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "]." + key + " in zolt.toml. Use a non-empty string value.");
            }
            values.put(key, value);
        }
        return values;
    }

    static Map<String, List<String>> stringListMap(TomlTable table, String section) {
        if (table == null) {
            return Map.of();
        }

        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object rawValue = table.get(List.of(key));
            if (!(rawValue instanceof TomlArray array)) {
                throw new ZoltConfigException(
                        "Invalid value for [" + section + "]." + key + " in zolt.toml. Use an array of strings.");
            }
            List<String> locks = new ArrayList<>();
            for (int index = 0; index < array.size(); index++) {
                Object element = array.get(index);
                if (!(element instanceof String value) || value.isBlank()) {
                    throw new ZoltConfigException(
                            "Invalid value for [" + section + "]." + key + "[" + index + "] in zolt.toml. Use a non-empty string.");
                }
                locks.add(value);
            }
            values.put(key, List.copyOf(locks));
        }
        return Map.copyOf(values);
    }

    static List<String> stringListOrDefault(
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
}
