package com.zolt.toml;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import com.zolt.project.TestRuntimeSettings;
import com.zolt.project.TestSuiteSettings;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.TomlTable;

final class BuildSectionCodec {
    private static final Set<String> BUILD_KEYS = Set.of("source", "test", "outputRoot", "output", "testOutput", "metadata");
    private static final Set<String> INTEGRATION_TEST_KEYS =
            Set.of("source", "sources", "resources", "output");
    private static final Set<String> TEST_RUNTIME_KEYS =
            Set.of("jvmArgs", "systemProperties", "environment", "events");
    private static final Set<String> TEST_SUITE_KEYS =
            Set.of("includeClassname", "excludeClassname", "includeTag", "excludeTag");
    private static final Set<String> BUILD_METADATA_KEYS = Set.of("buildInfo", "git", "reproducible");

    private BuildSectionCodec() {
    }

    static BuildSettings parseBuild(TomlTable table) {
        BuildSettings defaults = BuildSettings.defaults();
        if (table == null) {
            return defaults;
        }

        TomlValidation.validateKeysWithVersionRefHint("build", table, BUILD_KEYS);
        BuildMetadataSettings metadata = parseBuildMetadata(optionalTable(table, "metadata"));
        String source = TomlScalars.stringOrDefault(table, "build", "source", defaults.source());
        String test = TomlScalars.stringOrDefault(table, "build", "test", defaults.test());
        String outputRoot = TomlScalars.nonBlankStringOrDefault(table, "build", "outputRoot", defaults.outputRoot());
        validateOutputRoot(outputRoot);
        validateSupportedSourceRoot("[build].source", source);
        validateSupportedSourceRoot("[build].test", test);
        return new BuildSettings(
                source,
                test,
                outputRoot,
                TomlScalars.stringOrDefault(table, "build", "output", outputRoot + "/classes"),
                TomlScalars.stringOrDefault(table, "build", "testOutput", outputRoot + "/test-classes"),
                defaults.testSources(),
                defaults.groovyTestSources(),
                defaults.resourceRoots(),
                defaults.testResourceRoots(),
                metadata);
    }

    static BuildSettings parseTestSources(TomlTable testTable, BuildSettings build) {
        if (testTable == null) {
            return build;
        }
        TomlTable sourcesTable = optionalTable(testTable, "sources");
        if (sourcesTable == null) {
            return build;
        }
        validateSupportedTestSourceLanguages(sourcesTable);
        TomlValidation.validateKeysWithVersionRefHint("test.sources", sourcesTable, Set.of("java", "groovy"));
        List<String> javaSources =
                TomlScalars.stringListOrDefault(sourcesTable, "test.sources", "java", build.testSources());
        validateSupportedSourceRoots("[test.sources].java", javaSources);
        return new BuildSettings(
                build.source(),
                build.test(),
                build.outputRoot(),
                build.output(),
                build.testOutput(),
                javaSources,
                TomlScalars.stringListOrDefault(sourcesTable, "test.sources", "groovy", build.groovyTestSources()),
                build.integrationTestOutput(),
                build.integrationTestSources(),
                build.integrationTestResourceRoots(),
                build.resourceRoots(),
                build.testResourceRoots(),
                build.resourceFiltering(),
                build.testRuntime(),
                build.testSuites(),
                build.metadata(),
                build.generatedMainSources(),
                build.generatedTestSources());
    }

    static BuildSettings parseTestRuntime(TomlTable testTable, BuildSettings build) {
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

    static BuildSettings parseTestSuites(TomlTable testTable, BuildSettings build) {
        if (testTable == null) {
            return build;
        }
        TomlTable suitesTable = optionalTable(testTable, "suites");
        if (suitesTable == null) {
            return build;
        }
        Map<String, TestSuiteSettings> suites = new LinkedHashMap<>();
        for (String suiteName : suitesTable.keySet()) {
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
                suites.put(suiteName, new TestSuiteSettings(
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
                                List.of())));
            } catch (IllegalArgumentException exception) {
                throw new ZoltConfigException(exception.getMessage());
            }
        }
        return build.withTestSuites(suites);
    }

    static BuildSettings parseResourceRoots(TomlTable table, BuildSettings build) {
        return ResourceSectionCodec.parseResourceRoots(table, build);
    }

    static BuildSettings parseIntegrationTest(TomlTable table, BuildSettings build) {
        if (table == null) {
            return build;
        }
        TomlValidation.validateKeysWithVersionRefHint("integrationTest", table, INTEGRATION_TEST_KEYS);
        List<String> sources = TomlScalars.stringListOrDefault(
                table,
                "integrationTest",
                "sources",
                List.of(TomlScalars.stringOrDefault(
                        table,
                        "integrationTest",
                        "source",
                        build.integrationTestSources().isEmpty()
                                ? "src/integration-test/java"
                                : build.integrationTestSources().getFirst())));
        validateSupportedSourceRoots("[integrationTest].sources", sources);
        return build.withIntegrationTestSettings(
                TomlScalars.stringOrDefault(table, "integrationTest", "output", build.integrationTestOutput()),
                sources,
                TomlScalars.stringListOrDefault(
                        table,
                        "integrationTest",
                        "resources",
                        build.integrationTestResourceRoots()));
    }

    static void writeBuild(StringBuilder toml, BuildSettings build) {
        toml.append("[build]\n");
        writeAssignment(toml, "source", build.source());
        writeAssignment(toml, "test", build.test());
        writeAssignment(toml, "outputRoot", build.outputRoot());
        writeAssignment(toml, "output", build.output());
        writeAssignment(toml, "testOutput", build.testOutput());
    }

    static void writeBuildMetadata(StringBuilder toml, BuildMetadataSettings metadata) {
        if (metadata == null || metadata.equals(BuildMetadataSettings.defaults())) {
            return;
        }
        toml.append("\n[build.metadata]\n");
        writeAssignment(toml, "buildInfo", metadata.buildInfo());
        writeAssignment(toml, "git", metadata.git());
        writeAssignment(toml, "reproducible", metadata.reproducible());
    }

    static void writeTestSources(StringBuilder toml, BuildSettings build) {
        if (build.testSources().equals(List.of(build.test())) && build.groovyTestSources().isEmpty()) {
            return;
        }
        toml.append("[test.sources]\n");
        if (!build.testSources().equals(List.of(build.test()))) {
            writeStringArray(toml, "java", build.testSources());
        }
        if (!build.groovyTestSources().isEmpty()) {
            writeStringArray(toml, "groovy", build.groovyTestSources());
        }
        toml.append('\n');
    }

    static void writeTestRuntime(StringBuilder toml, TestRuntimeSettings runtime) {
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

    static void writeTestSuites(StringBuilder toml, Map<String, TestSuiteSettings> suites) {
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
            toml.append('\n');
        }
    }

    static void writeResources(StringBuilder toml, BuildSettings build) {
        ResourceSectionCodec.writeResources(toml, build);
    }

    private static BuildMetadataSettings parseBuildMetadata(TomlTable table) {
        BuildMetadataSettings defaults = BuildMetadataSettings.defaults();
        if (table == null) {
            return defaults;
        }
        TomlValidation.validateKeysWithVersionRefHint("build.metadata", table, BUILD_METADATA_KEYS);
        return new BuildMetadataSettings(
                TomlScalars.booleanOrDefault(table, "build.metadata", "buildInfo", defaults.buildInfo()),
                TomlScalars.booleanOrDefault(table, "build.metadata", "git", defaults.git()),
                TomlScalars.booleanOrDefault(table, "build.metadata", "reproducible", defaults.reproducible()));
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

    private static void validateSupportedTestSourceLanguages(TomlTable table) {
        for (String key : table.keySet()) {
            switch (key) {
                case "kotlin" -> throw new ZoltConfigException(
                        "Unsupported Kotlin test source roots [test.sources].kotlin in zolt.toml. Kotlin is not supported in the public beta. Use Java test roots under [test.sources].java or keep Kotlin modules outside the Zolt beta scope.");
                case "scala" -> throw new ZoltConfigException(
                        "Unsupported Scala test source roots [test.sources].scala in zolt.toml. Scala is not supported in the public beta. Use Java test roots under [test.sources].java or keep Scala modules outside the Zolt beta scope.");
                default -> {
                }
            }
        }
    }

    private static void validateSupportedSourceRoots(String subject, List<String> roots) {
        for (String root : roots) {
            validateSupportedSourceRoot(subject, root);
        }
    }

    private static void validateOutputRoot(String outputRoot) {
        Path path = Path.of(outputRoot).normalize();
        String normalized = outputRoot.replace('\\', '/');
        if (path.isAbsolute()
                || normalized.startsWith("/")
                || normalized.startsWith("\\\\")
                || normalized.matches("^[A-Za-z]:[\\\\/].*")
                || path.toString().equals(".")
                || path.startsWith("..")) {
            throw new ZoltConfigException(
                    "Invalid [build].outputRoot `"
                            + outputRoot
                            + "` in zolt.toml. Use a project-relative output directory such as `target` or `.zolt/build`.");
        }
    }

    private static void validateSupportedSourceRoot(String subject, String root) {
        String normalized = root.replace('\\', '/').toLowerCase();
        if (hasPathSegment(normalized, "kotlin") || normalized.endsWith(".kt")) {
            throw new ZoltConfigException(
                    "Unsupported Kotlin source root "
                            + subject
                            + " = \""
                            + root
                            + "\" in zolt.toml. Kotlin is not supported in the public beta. Use Java source roots such as src/main/java, or keep Kotlin modules outside the Zolt beta scope.");
        }
        if (hasPathSegment(normalized, "scala") || normalized.endsWith(".scala")) {
            throw new ZoltConfigException(
                    "Unsupported Scala source root "
                            + subject
                            + " = \""
                            + root
                            + "\" in zolt.toml. Scala is not supported in the public beta. Use Java source roots such as src/main/java, or keep Scala modules outside the Zolt beta scope.");
        }
        if (normalized.startsWith("src/android/")
                || normalized.contains("/src/android/")
                || normalized.contains("/android/")
                || normalized.startsWith("android/")) {
            throw new ZoltConfigException(
                    "Unsupported Android source root "
                            + subject
                            + " = \""
                            + root
                            + "\" in zolt.toml. Android projects are not supported in the public beta. Use normal Java application source roots, or keep Android modules outside the Zolt beta scope.");
        }
    }

    private static boolean hasPathSegment(String path, String segment) {
        return path.equals(segment)
                || path.startsWith(segment + "/")
                || path.endsWith("/" + segment)
                || path.contains("/" + segment + "/");
    }

    private static void writeAssignment(StringBuilder toml, String key, boolean value) {
        toml.append(key).append(" = ").append(value).append('\n');
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
