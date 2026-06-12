package com.zolt.toml;

import com.zolt.project.CompilerSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

final class CompilerSectionCodec {
    private static final Set<String> COMPILER_KEYS = Set.of(
            "generatedSources",
            "generatedTestSources",
            "release",
            "encoding",
            "args",
            "testArgs");
    private static final Set<String> ZOLT_OWNED_JAVAC_ARGS = Set.of(
            "--release",
            "-source",
            "--source",
            "-target",
            "--target",
            "-encoding",
            "-d",
            "-classpath",
            "--class-path",
            "-cp",
            "-processorpath",
            "-processorpath:",
            "--processor-path",
            "-processor",
            "-s",
            "-sourcepath",
            "--source-path");

    private CompilerSectionCodec() {
    }

    static CompilerSettings parse(TomlTable table) {
        CompilerSettings defaults = CompilerSettings.defaults();
        if (table == null) {
            return defaults;
        }

        validateKeys("compiler", table, COMPILER_KEYS);
        List<String> args = stringListOrDefault(table, "compiler", "args", defaults.args());
        List<String> testArgs = stringListOrDefault(table, "compiler", "testArgs", defaults.testArgs());
        validateCompilerArgs("args", args);
        validateCompilerArgs("testArgs", testArgs);
        return new CompilerSettings(
                stringOrDefault(table, "compiler", "generatedSources", defaults.generatedSources()),
                stringOrDefault(table, "compiler", "generatedTestSources", defaults.generatedTestSources()),
                stringOrDefault(table, "compiler", "release", defaults.release()),
                stringOrDefault(table, "compiler", "encoding", defaults.encoding()),
                args,
                testArgs);
    }

    static void write(StringBuilder toml, CompilerSettings settings) {
        CompilerSettings defaults = CompilerSettings.defaults();
        if (settings == null || settings.equals(defaults)) {
            return;
        }
        toml.append("\n[compiler]\n");
        writeAssignment(toml, "generatedSources", settings.generatedSources());
        writeAssignment(toml, "generatedTestSources", settings.generatedTestSources());
        if (!settings.release().isBlank()) {
            writeAssignment(toml, "release", settings.release());
        }
        if (!settings.encoding().isBlank()) {
            writeAssignment(toml, "encoding", settings.encoding());
        }
        if (!settings.args().isEmpty()) {
            writeStringArray(toml, "args", settings.args());
        }
        if (!settings.testArgs().isEmpty()) {
            writeStringArray(toml, "testArgs", settings.testArgs());
        }
    }

    private static void validateCompilerArgs(String field, List<String> args) {
        for (String arg : args) {
            String flag = arg.contains("=") ? arg.substring(0, arg.indexOf('=')) : arg;
            if (ZOLT_OWNED_JAVAC_ARGS.contains(flag)) {
                throw new ZoltConfigException(
                        "Invalid value for [compiler]."
                                + field
                                + " in zolt.toml. Zolt owns `"
                                + flag
                                + "`; use [compiler].release, [compiler].encoding, source roots, dependencies, or annotation processor settings instead.");
            }
        }
    }

    private static void validateKeys(String section, TomlTable table, Set<String> allowedKeys) {
        for (String key : table.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new ZoltConfigException(
                        "Unknown field [" + section + "]." + key + " in zolt.toml. Remove it or check the spelling.");
            }
        }
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
