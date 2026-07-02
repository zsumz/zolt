package sh.zolt.toml;

import sh.zolt.toml.support.TomlValidation;
import sh.zolt.toml.support.TomlScalars;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.TestRuntimeSettings;
import sh.zolt.project.TestSuiteSettings;
import sh.zolt.toml.testsection.TestRuntimeSectionCodec;
import sh.zolt.toml.testsection.TestSuiteSectionCodec;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.TomlTable;

final class TestSectionCodec {
    private TestSectionCodec() {
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
        BuildSectionCodec.validateSupportedSourceRoots("[test.sources].java", javaSources);
        return new BuildSettings(
                build.source(),
                build.sourceRoots(),
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
        return TestRuntimeSectionCodec.parse(testTable, build);
    }

    static BuildSettings parseTestSuites(TomlTable testTable, BuildSettings build) {
        return TestSuiteSectionCodec.parse(testTable, build);
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
        TestRuntimeSectionCodec.write(toml, runtime);
    }

    static void writeTestSuites(StringBuilder toml, Map<String, TestSuiteSettings> suites) {
        TestSuiteSectionCodec.write(toml, suites);
    }

    private static TomlTable optionalTable(TomlTable table, String key) {
        return table.getTable(List.of(key));
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
