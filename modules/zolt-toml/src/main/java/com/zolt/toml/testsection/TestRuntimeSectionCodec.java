package com.zolt.toml.testsection;

import com.zolt.project.BuildSettings;
import com.zolt.project.TestRuntimeSettings;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.support.TomlScalars;
import com.zolt.toml.support.TomlValidation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlTable;

public final class TestRuntimeSectionCodec {
    private static final Set<String> TEST_RUNTIME_KEYS =
            Set.of("jvmArgs", "systemProperties", "environment", "events");

    private TestRuntimeSectionCodec() {
    }

    public static BuildSettings parse(TomlTable testTable, BuildSettings build) {
        if (testTable == null) {
            return build;
        }
        TomlTable runtimeTable = optionalTable(testTable, "runtime");
        if (runtimeTable == null) {
            return build;
        }
        TomlValidation.validateKeysWithVersionRefHint("test.runtime", runtimeTable, TEST_RUNTIME_KEYS);
        try {
            return build.withTestRuntime(new TestRuntimeSettings(
                    TomlScalars.stringListOrDefault(runtimeTable, "test.runtime", "jvmArgs", List.of()),
                    TomlScalars.stringMap(optionalTable(runtimeTable, "systemProperties"), "test.runtime.systemProperties"),
                    TomlScalars.stringMap(optionalTable(runtimeTable, "environment"), "test.runtime.environment"),
                    TomlScalars.stringListOrDefault(runtimeTable, "test.runtime", "events", List.of())));
        } catch (IllegalArgumentException exception) {
            throw new ZoltConfigException(exception.getMessage());
        }
    }

    public static void write(StringBuilder toml, TestRuntimeSettings runtime) {
        if (runtime == null || runtime.defaultsOnly()) {
            return;
        }
        toml.append("[test.runtime]\n");
        if (!runtime.jvmArgs().isEmpty()) {
            writeStringArray(toml, "jvmArgs", runtime.jvmArgs());
        }
        if (!runtime.systemProperties().isEmpty()) {
            writeInlineStringMap(toml, "systemProperties", runtime.systemProperties());
        }
        if (!runtime.environment().isEmpty()) {
            writeInlineStringMap(toml, "environment", runtime.environment());
        }
        if (!runtime.events().isEmpty()) {
            writeStringArray(toml, "events", runtime.events());
        }
        toml.append('\n');
    }

    private static TomlTable optionalTable(TomlTable table, String key) {
        return table.getTable(List.of(key));
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

    private static void writeInlineStringMap(StringBuilder toml, String key, Map<String, String> values) {
        toml.append(key).append(" = { ");
        int index = 0;
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            if (index > 0) {
                toml.append(", ");
            }
            toml.append(quote(entry.getKey())).append(" = ").append(quote(entry.getValue()));
            index++;
        }
        toml.append(" }\n");
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
