package sh.zolt.toml;

import static sh.zolt.toml.ProjectConfigFixture.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.BuildMetadataSettings;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.DependencySection;
import sh.zolt.project.FrameworkSettings;
import sh.zolt.project.NativeSettings;
import sh.zolt.project.PackageMode;
import sh.zolt.project.PackageSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.PublicationMetadata;
import sh.zolt.project.QuarkusPackageMode;
import sh.zolt.project.QuarkusSettings;
import sh.zolt.project.SpringBootSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ZoltTomlPackageFrameworkWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void writesNativeSettingsWhenConfigured() {
        ProjectConfig original = configWithNativeSettings();

        ProjectConfig parsed = parser.parse(writer.write(original));

        assertEquals(original.nativeSettings(), parsed.nativeSettings());
    }

    @Test
    void writesPackageSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[package]\n"));
        assertTrue(toml.contains("mode = \"spring-boot\""));
        assertFalse(toml.contains("[package.metadata]"));
        assertEquals(original.packageSettings(), parsed.packageSettings());
    }

    @Test
    void writesSpringBootWarPackageSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT_WAR));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[package]\n"));
        assertTrue(toml.contains("mode = \"spring-boot-war\""));
        assertEquals(original.packageSettings(), parsed.packageSettings());
    }

    @Test
    void writesLibraryPackageSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", null)
                .withPackageSettings(new PackageSettings(
                        PackageMode.THIN,
                        true,
                        true,
                        true,
                        new PublicationMetadata(
                                "Hello Library",
                                "Demo library",
                                "https://example.com/hello",
                                "Apache-2.0",
                                List.of("Shawn"),
                                "https://example.com/hello.git",
                                "https://example.com/hello/issues"),
                        Map.of(
                                "Automatic-Module-Name", "com.example.hello",
                                "Bundle-SymbolicName", "com.example.hello")));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[package]\n"));
        assertTrue(toml.contains("sources = true"));
        assertTrue(toml.contains("javadoc = true"));
        assertTrue(toml.contains("tests = true"));
        assertTrue(toml.contains("[package.metadata]\n"));
        assertTrue(toml.contains("name = \"Hello Library\""));
        assertTrue(toml.contains("developers = [\"Shawn\"]"));
        assertTrue(toml.contains("[package.manifest]\n"));
        assertTrue(toml.contains("\"Automatic-Module-Name\" = \"com.example.hello\""));
        assertTrue(toml.contains("\"Bundle-SymbolicName\" = \"com.example.hello\""));
        assertEquals(original.packageSettings(), parsed.packageSettings());
    }

    @Test
    void writesQuarkusFrameworkSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[framework.quarkus]\n"));
        assertTrue(toml.contains("enabled = true"));
        assertTrue(toml.contains("package = \"fast-jar\""));
        assertEquals(original.frameworkSettings(), parsed.frameworkSettings());
    }

    @Test
    void writesSpringBootNativeFrameworkSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withFrameworkSettings(new FrameworkSettings(
                        new SpringBootSettings(true),
                        QuarkusSettings.defaults()));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[framework.springBoot.native]\n"));
        assertTrue(toml.contains("enabled = true"));
        assertEquals(original.frameworkSettings(), parsed.frameworkSettings());
    }

    @Test
    void writesBuildMetadataSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        new BuildMetadataSettings(true, true, true)));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[build.metadata]\n"));
        assertTrue(toml.contains("buildInfo = true"));
        assertTrue(toml.contains("git = true"));
        assertTrue(toml.contains("reproducible = true"));
        assertEquals(original.build().metadata(), parsed.build().metadata());
    }

    @Test
    void preservesPackageSettingsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withPackageSettings(new PackageSettings(PackageMode.UBER));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.packageSettings(), parsed.packageSettings());
    }

    @Test
    void preservesFrameworkSettingsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
        config = config.withBuildSettings(BuildSettings.defaults());
        config = writer.addDependency(config, DependencySection.MAIN, "io.quarkus:quarkus-rest", "3.28.2");
        config = writer.addManagedDependency(config, DependencySection.TEST, "io.quarkus:quarkus-junit5");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.frameworkSettings(), parsed.frameworkSettings());
    }

    @Test
    void preservesBuildMetadataSettingsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        new BuildMetadataSettings(true, false, true)));
        config = writer.addDependency(config, DependencySection.MAIN, "com.example:app", "1.0.0");
        config = writer.addManagedDependency(config, DependencySection.TEST, "com.example:test-helper");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.build().metadata(), parsed.build().metadata());
    }

    @Test
    void preservesNativeSettingsWhenEditingDependencies() {
        ProjectConfig config = configWithNativeSettings();
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");
        config = writer.removeDependency(config, DependencySection.MAIN, "com.google.guava:guava");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.nativeSettings(), parsed.nativeSettings());
    }

    private static ProjectConfig configWithNativeSettings() {
        return config()
                .nativeSettings(new NativeSettings("hello-native", "target/native-custom", List.of("--no-fallback")))
                .build();
    }
}
