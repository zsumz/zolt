package com.zolt.toml;

import static com.zolt.toml.ProjectConfigFixture.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencySection;
import com.zolt.project.NativeSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.TestSuiteSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ZoltTomlWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void writesDefaultConfig() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");

        String toml = writer.write(config);

        assertEquals("""
                [project]
                name = "hello"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                main = "com.example.Main"

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [platforms]

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                outputRoot = "target"
                output = "target/classes"
                testOutput = "target/test-classes"
                """, toml);
    }

    @Test
    void writtenConfigParsesBackIntoModel() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        ProjectConfig parsed = parser.parse(writer.write(original));

        assertEquals(original.project(), parsed.project());
        assertEquals(original.repositories(), parsed.repositories());
        assertEquals(original.versionAliases(), parsed.versionAliases());
        assertEquals(original.platforms(), parsed.platforms());
        assertEquals(original.apiDependencies(), parsed.apiDependencies());
        assertEquals(original.managedApiDependencies(), parsed.managedApiDependencies());
        assertEquals(original.workspaceApiDependencies(), parsed.workspaceApiDependencies());
        assertEquals(original.dependencies(), parsed.dependencies());
        assertEquals(original.managedDependencies(), parsed.managedDependencies());
        assertEquals(original.workspaceDependencies(), parsed.workspaceDependencies());
        assertEquals(original.testDependencies(), parsed.testDependencies());
        assertEquals(original.managedTestDependencies(), parsed.managedTestDependencies());
        assertEquals(original.workspaceTestDependencies(), parsed.workspaceTestDependencies());
        assertEquals(original.annotationProcessors(), parsed.annotationProcessors());
        assertEquals(original.managedAnnotationProcessors(), parsed.managedAnnotationProcessors());
        assertEquals(original.testAnnotationProcessors(), parsed.testAnnotationProcessors());
        assertEquals(original.managedTestAnnotationProcessors(), parsed.managedTestAnnotationProcessors());
        assertEquals(original.dependencyPolicy(), parsed.dependencyPolicy());
        assertEquals(original.build(), parsed.build());
        assertEquals(original.compilerSettings(), parsed.compilerSettings());
        assertEquals(original.packageSettings(), parsed.packageSettings());
    }

    @Test
    void writesCompilerSettingsWhenConfigured() {
        ProjectConfig config = configWithCompilerSettings();

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[compiler]\n"));
        assertTrue(toml.contains("generatedSources = \"build/generated/main\""));
        assertTrue(toml.contains("generatedTestSources = \"build/generated/test\""));
        assertTrue(toml.contains("release = \"17\""));
        assertTrue(toml.contains("encoding = \"UTF-8\""));
        assertTrue(toml.contains("args = [\"-Xlint:deprecation\", \"-parameters\"]"));
        assertTrue(toml.contains("testArgs = [\"-Xlint:unchecked\"]"));
        assertEquals(config.compilerSettings(), parsed.compilerSettings());
    }

    @Test
    void preservesCompilerSettingsWhenEditingDependencies() {
        ProjectConfig config = configWithCompilerSettings();
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.compilerSettings(), parsed.compilerSettings());
    }

    @Test
    void preservesExplicitJavaTestSourceRootsWhenEditingDependencies() {
        ProjectConfig config = config()
                .build(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java", "src/integration-test/java")))
                .build();
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[test.sources]\njava = [\"src/test/java\", \"src/integration-test/java\"]"));
        assertEquals(config.build().testSources(), parsed.build().testSources());
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
    }

    @Test
    void writesOutputRootAndDerivedOutputs() {
        ProjectConfig config = config()
                .build(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        ".zolt/build",
                        ".zolt/build/classes",
                        ".zolt/build/test-classes"))
                .compilerSettings(CompilerSettings.defaultsForOutputRoot(".zolt/build"))
                .nativeSettings(NativeSettings.defaultsForOutputRoot(".zolt/build"))
                .build();

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("outputRoot = \".zolt/build\""));
        assertEquals(".zolt/build", parsed.build().outputRoot());
        assertEquals(".zolt/build/classes", parsed.build().output());
        assertEquals(".zolt/build/test-classes", parsed.build().testOutput());
        assertEquals(".zolt/build/generated/sources/annotations", parsed.compilerSettings().generatedSources());
        assertEquals(".zolt/build/generated/test-sources/annotations", parsed.compilerSettings().generatedTestSources());
        assertEquals(".zolt/build/native", parsed.nativeSettings().output());
        assertFalse(toml.contains("[compiler]"));
        assertFalse(toml.contains("[native]"));
    }

    @Test
    void preservesExplicitGroovyTestSourceRootsWhenEditingDependencies() {
        ProjectConfig config = config()
                .build(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        List.of("src/test/groovy")))
                .build();
        config = writer.addDependency(config, DependencySection.TEST, "org.spockframework:spock-core", "2.4-M5-groovy-4.0");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[test.sources]\ngroovy = [\"src/test/groovy\"]"));
        assertEquals(config.build().groovyTestSources(), parsed.build().groovyTestSources());
        assertEquals("2.4-M5-groovy-4.0", parsed.testDependencies().get("org.spockframework:spock-core"));
    }

    @Test
    void preservesTestSuitesWhenEditingDependencies() {
        ProjectConfig config = config()
                .build(BuildSettings.defaults().withTestSuites(Map.of(
                        "fast",
                        new TestSuiteSettings(
                                List.of("*Test", "*Spec"),
                                List.of("*ContractTest"),
                                List.of("fast"),
                                List.of("slow"),
                                true,
                                4,
                                Map.of(
                                        "com.example.DbTest",
                                        List.of("database"),
                                        "com.example.KafkaSpec",
                                        List.of("kafka", "topic"))))))
                .build();
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[test.suites.fast]\n"));
        assertTrue(toml.contains("includeClassname = [\"*Test\", \"*Spec\"]"));
        assertTrue(toml.contains("excludeClassname = [\"*ContractTest\"]"));
        assertTrue(toml.contains("includeTag = [\"fast\"]"));
        assertTrue(toml.contains("excludeTag = [\"slow\"]"));
        assertTrue(toml.contains("parallelSafe = true"));
        assertTrue(toml.contains("maxWorkers = 4"));
        assertTrue(toml.contains("resourceLocks = { \"com.example.DbTest\" = [\"database\"], \"com.example.KafkaSpec\" = [\"kafka\", \"topic\"] }"));
        assertEquals(config.build().testSuites(), parsed.build().testSuites());
    }

    @Test
    void removesDependenciesFromCorrectSections() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");
        config = writer.removeDependency(config, DependencySection.MAIN, "com.google.guava:guava");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertTrue(parsed.dependencies().isEmpty());
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
    }

    @Test
    void formatsDependenciesDeterministically() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addDependency(config, DependencySection.MAIN, "org.slf4j:slf4j-api", "2.0.16");
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");

        String toml = writer.write(config);

        assertTrue(toml.indexOf("\"com.google.guava:guava\"") < toml.indexOf("\"org.slf4j:slf4j-api\""));
    }

    private static ProjectConfig configWithCompilerSettings() {
        return config()
                .compilerSettings(new CompilerSettings(
                        "build/generated/main",
                        "build/generated/test",
                        "17",
                        "UTF-8",
                        List.of("-Xlint:deprecation", "-parameters"),
                        List.of("-Xlint:unchecked")))
                .build();
    }
}
