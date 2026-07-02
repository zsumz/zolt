package sh.zolt.toml;

import sh.zolt.toml.support.TomlValidation;
import sh.zolt.toml.support.TomlScalars;
import sh.zolt.project.BuildMetadataSettings;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.TestRuntimeSettings;
import sh.zolt.project.TestSuiteSettings;
import sh.zolt.toml.resources.ResourceSectionCodec;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.TomlTable;

final class BuildSectionCodec {
    private static final Set<String> BUILD_KEYS =
            Set.of("source", "sources", "test", "outputRoot", "output", "testOutput", "metadata");
    private static final Set<String> INTEGRATION_TEST_KEYS =
            Set.of("source", "sources", "resources", "output");
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
        List<String> sourceRoots = TomlScalars.stringListOrDefault(table, "build", "sources", List.of(source));
        if (sourceRoots.isEmpty()) {
            throw new ZoltConfigException(
                    "Invalid value for [build].sources in zolt.toml. Use a non-empty array of source root strings.");
        }
        if (table.contains("source") && table.contains("sources") && !source.equals(sourceRoots.getFirst())) {
            throw new ZoltConfigException(
                    "Invalid [build] source roots in zolt.toml. When both [build].source and [build].sources are set, [build].source must match the first [build].sources entry.");
        }
        source = sourceRoots.getFirst();
        String test = TomlScalars.stringOrDefault(table, "build", "test", defaults.test());
        String outputRoot = TomlScalars.nonBlankStringOrDefault(table, "build", "outputRoot", defaults.outputRoot());
        validateOutputRoot(outputRoot);
        validateSupportedSourceRoots("[build].sources", sourceRoots);
        validateSupportedSourceRoot("[build].test", test);
        return new BuildSettings(
                source,
                sourceRoots,
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
        return TestSectionCodec.parseTestSources(testTable, build);
    }

    static BuildSettings parseTestRuntime(TomlTable testTable, BuildSettings build) {
        return TestSectionCodec.parseTestRuntime(testTable, build);
    }

    static BuildSettings parseTestSuites(TomlTable testTable, BuildSettings build) {
        return TestSectionCodec.parseTestSuites(testTable, build);
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
        if (!build.sourceRoots().equals(List.of(build.source()))) {
            writeStringArray(toml, "sources", build.sourceRoots());
        }
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
        TestSectionCodec.writeTestSources(toml, build);
    }

    static void writeTestRuntime(StringBuilder toml, TestRuntimeSettings runtime) {
        TestSectionCodec.writeTestRuntime(toml, runtime);
    }

    static void writeTestSuites(StringBuilder toml, Map<String, TestSuiteSettings> suites) {
        TestSectionCodec.writeTestSuites(toml, suites);
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

    static void validateSupportedSourceRoots(String subject, List<String> roots) {
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

    private static void writeAssignment(StringBuilder toml, String key, int value) {
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
