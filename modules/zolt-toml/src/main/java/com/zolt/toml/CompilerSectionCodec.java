package com.zolt.toml;

import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import java.util.List;
import java.util.Set;
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
        return parse(table, BuildSettings.defaults());
    }

    static CompilerSettings parse(TomlTable table, BuildSettings build) {
        CompilerSettings defaults = CompilerSettings.defaultsForOutputRoot(build.outputRoot());
        if (table == null) {
            return defaults;
        }

        TomlValidation.validateKeys("compiler", table, COMPILER_KEYS);
        List<String> args = TomlScalars.stringListOrDefault(table, "compiler", "args", defaults.args());
        List<String> testArgs = TomlScalars.stringListOrDefault(table, "compiler", "testArgs", defaults.testArgs());
        validateCompilerArgs("args", args);
        validateCompilerArgs("testArgs", testArgs);
        return new CompilerSettings(
                TomlScalars.nonBlankStringOrDefault(
                        table,
                        "compiler",
                        "generatedSources",
                        defaults.generatedSources()),
                TomlScalars.nonBlankStringOrDefault(
                        table,
                        "compiler",
                        "generatedTestSources",
                        defaults.generatedTestSources()),
                TomlScalars.nonBlankStringOrDefault(table, "compiler", "release", defaults.release()),
                TomlScalars.nonBlankStringOrDefault(table, "compiler", "encoding", defaults.encoding()),
                args,
                testArgs);
    }

    static void write(StringBuilder toml, CompilerSettings settings) {
        write(toml, settings, BuildSettings.defaults());
    }

    static void write(StringBuilder toml, CompilerSettings settings, BuildSettings build) {
        CompilerSettings defaults = CompilerSettings.defaultsForOutputRoot(build.outputRoot());
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
