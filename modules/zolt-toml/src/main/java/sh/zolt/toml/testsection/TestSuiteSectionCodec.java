package sh.zolt.toml.testsection;

import sh.zolt.project.BuildSettings;
import sh.zolt.project.TestSuiteSettings;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.support.TomlScalars;
import sh.zolt.toml.support.TomlValidation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlTable;

public final class TestSuiteSectionCodec {
    private static final Set<String> TEST_SUITE_KEYS =
            Set.of(
                    "includeClassname",
                    "excludeClassname",
                    "includeTag",
                    "excludeTag",
                    "parallelSafe",
                    "maxWorkers",
                    "resourceLocks");

    private TestSuiteSectionCodec() {
    }

    public static BuildSettings parse(TomlTable testTable, BuildSettings build) {
        if (testTable == null) {
            return build;
        }
        TomlTable suitesTable = optionalTable(testTable, "suites");
        if (suitesTable == null) {
            return build;
        }
        Map<String, TestSuiteSettings> suites = new LinkedHashMap<>();
        for (String suiteName : suitesTable.keySet()) {
            suites.put(suiteName, parseTestSuite(suitesTable, suiteName));
        }
        return build.withTestSuites(suites);
    }

    public static void write(StringBuilder toml, Map<String, TestSuiteSettings> suites) {
        if (suites == null || suites.isEmpty()) {
            return;
        }
        for (Map.Entry<String, TestSuiteSettings> entry : suites.entrySet()) {
            TestSuiteSettings suite = entry.getValue();
            toml.append("[test.suites.").append(entry.getKey()).append("]\n");
            if (!suite.includeClassname().isEmpty()) {
                writeStringArray(toml, "includeClassname", suite.includeClassname());
            }
            if (!suite.excludeClassname().isEmpty()) {
                writeStringArray(toml, "excludeClassname", suite.excludeClassname());
            }
            if (!suite.includeTag().isEmpty()) {
                writeStringArray(toml, "includeTag", suite.includeTag());
            }
            if (!suite.excludeTag().isEmpty()) {
                writeStringArray(toml, "excludeTag", suite.excludeTag());
            }
            if (suite.parallelSafe()) {
                writeAssignment(toml, "parallelSafe", true);
            }
            if (suite.maxWorkers() != 1) {
                writeAssignment(toml, "maxWorkers", suite.maxWorkers());
            }
            if (!suite.resourceLocks().isEmpty()) {
                writeInlineStringListMap(toml, "resourceLocks", suite.resourceLocks());
            }
            toml.append('\n');
        }
    }

    private static TestSuiteSettings parseTestSuite(TomlTable suitesTable, String suiteName) {
        validateSuiteName(suiteName);
        TomlTable suiteTable = optionalTable(suitesTable, suiteName);
        if (suiteTable == null) {
            throw new ZoltConfigException(
                    "Invalid value for [test.suites]."
                            + suiteName
                            + " in zolt.toml. Use a table such as [test.suites."
                            + suiteName
                            + "].");
        }
        TomlValidation.validateKeysWithVersionRefHint("test.suites." + suiteName, suiteTable, TEST_SUITE_KEYS);
        try {
            return new TestSuiteSettings(
                    TomlScalars.stringListOrDefault(
                            suiteTable,
                            "test.suites." + suiteName,
                            "includeClassname",
                            List.of()),
                    TomlScalars.stringListOrDefault(
                            suiteTable,
                            "test.suites." + suiteName,
                            "excludeClassname",
                            List.of()),
                    TomlScalars.stringListOrDefault(
                            suiteTable,
                            "test.suites." + suiteName,
                            "includeTag",
                            List.of()),
                    TomlScalars.stringListOrDefault(
                            suiteTable,
                            "test.suites." + suiteName,
                            "excludeTag",
                            List.of()),
                    TomlScalars.booleanOrDefault(
                            suiteTable,
                            "test.suites." + suiteName,
                            "parallelSafe",
                            false),
                    TomlScalars.integerOrDefault(
                            suiteTable,
                            "test.suites." + suiteName,
                            "maxWorkers",
                            1),
                    TomlScalars.stringListMap(
                            optionalTable(suiteTable, "resourceLocks"),
                            "test.suites." + suiteName + ".resourceLocks"));
        } catch (IllegalArgumentException exception) {
            throw new ZoltConfigException(exception.getMessage());
        }
    }

    private static TomlTable optionalTable(TomlTable table, String key) {
        return table.getTable(List.of(key));
    }

    private static void validateSuiteName(String suiteName) {
        if (suiteName == null || suiteName.isBlank()) {
            throw new ZoltConfigException("Invalid test suite name in zolt.toml. Use a non-empty suite name.");
        }
        if ("all".equals(suiteName)) {
            throw new ZoltConfigException(
                    "Invalid test suite name `all` in zolt.toml. `all` is reserved for Zolt's default aggregate suite.");
        }
        if (!suiteName.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new ZoltConfigException(
                    "Invalid test suite name `"
                            + suiteName
                            + "` in zolt.toml. Use letters, digits, `_`, or `-`, starting with a letter.");
        }
    }

    private static void writeAssignment(StringBuilder toml, String key, boolean value) {
        toml.append(key).append(" = ").append(value).append('\n');
    }

    private static void writeAssignment(StringBuilder toml, String key, int value) {
        toml.append(key).append(" = ").append(value).append('\n');
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

    private static void writeInlineStringListMap(StringBuilder toml, String key, Map<String, List<String>> values) {
        toml.append(key).append(" = { ");
        int index = 0;
        for (Map.Entry<String, List<String>> entry : new TreeMap<>(values).entrySet()) {
            if (index > 0) {
                toml.append(", ");
            }
            toml.append(quote(entry.getKey())).append(" = [");
            for (int valueIndex = 0; valueIndex < entry.getValue().size(); valueIndex++) {
                if (valueIndex > 0) {
                    toml.append(", ");
                }
                toml.append(quote(entry.getValue().get(valueIndex)));
            }
            toml.append("]");
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
