package com.zolt.toml;

import com.zolt.toml.support.TomlValidation;
import com.zolt.toml.support.TomlScalars;
import com.zolt.project.BuildSettings;
import com.zolt.project.NativeSettings;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlTable;

final class NativeSectionCodec {
    private static final Set<String> NATIVE_KEYS = Set.of("imageName", "output", "args");

    private NativeSectionCodec() {
    }

    static NativeSettings parse(TomlTable table, String projectName) {
        return parse(table, projectName, BuildSettings.defaults());
    }

    static NativeSettings parse(TomlTable table, String projectName, BuildSettings build) {
        NativeSettings buildDefaults = NativeSettings.defaultsForOutputRoot(build.outputRoot());
        if (table == null) {
            return buildDefaults;
        }

        NativeSettings defaults = buildDefaults.withDefaultImageName(projectName);
        TomlValidation.validateKeys("native", table, NATIVE_KEYS);
        return new NativeSettings(
                TomlScalars.nonBlankStringOrDefault(table, "native", "imageName", defaults.imageName()),
                TomlScalars.nonBlankStringOrDefault(table, "native", "output", defaults.output()),
                TomlScalars.stringListOrDefault(table, "native", "args", defaults.args()));
    }

    static void write(StringBuilder toml, NativeSettings nativeSettings) {
        write(toml, nativeSettings, BuildSettings.defaults());
    }

    static void write(StringBuilder toml, NativeSettings nativeSettings, BuildSettings build) {
        NativeSettings defaults = NativeSettings.defaultsForOutputRoot(build.outputRoot());
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
