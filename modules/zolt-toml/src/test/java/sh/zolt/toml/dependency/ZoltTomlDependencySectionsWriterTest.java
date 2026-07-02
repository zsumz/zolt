package sh.zolt.toml.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.DependencySection;
import sh.zolt.project.ProjectConfig;
import org.junit.jupiter.api.Test;

final class ZoltTomlDependencySectionsWriterTest extends ZoltTomlDependencySectionsWriterTestSupport {

    @Test
    void writesApiDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "web"
                version = "0.1.0"
                group = "com.acme"
                java = "21"

                [api.dependencies]
                "com.acme:managed-contract" = {}
                "com.acme:shared-contract" = { workspace = "modules/shared-contract" }
                "com.fasterxml.jackson.core:jackson-annotations" = "2.20.0"
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[api.dependencies]\n"));
        assertTrue(toml.contains("\"com.acme:managed-contract\" = {}"));
        assertTrue(toml.contains("\"com.acme:shared-contract\" = { workspace = \"modules/shared-contract\" }"));
        assertTrue(toml.contains("\"com.fasterxml.jackson.core:jackson-annotations\" = \"2.20.0\""));
        assertEquals(config.apiDependencies(), parsed.apiDependencies());
        assertEquals(config.managedApiDependencies(), parsed.managedApiDependencies());
        assertEquals(config.workspaceApiDependencies(), parsed.workspaceApiDependencies());
    }

    @Test
    void writesRuntimeAndProvidedDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("web", "com.acme", "com.acme.Main");
        config = writer.addManagedDependency(config, DependencySection.RUNTIME, "com.h2database:h2");
        config = writer.addDependency(
                config,
                DependencySection.PROVIDED,
                "jakarta.servlet:jakarta.servlet-api",
                "6.1.0");
        config = writer.addPlatform(config, "org.springframework.boot:spring-boot-dependencies", "4.0.6");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[runtime.dependencies]\n\"com.h2database:h2\" = {}"));
        assertTrue(toml.contains("[provided.dependencies]\n\"jakarta.servlet:jakarta.servlet-api\" = \"6.1.0\""));
        assertTrue(parsed.managedRuntimeDependencies().contains("com.h2database:h2"));
        assertEquals("6.1.0", parsed.providedDependencies().get("jakarta.servlet:jakarta.servlet-api"));
        assertEquals("4.0.6", parsed.platforms().get("org.springframework.boot:spring-boot-dependencies"));
    }

    @Test
    void writesDevDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("web", "com.acme", "com.acme.Main");
        config = writer.addManagedDependency(config, DependencySection.DEV, "org.springframework.boot:spring-boot-devtools");
        config = writer.addDependency(config, DependencySection.DEV, "com.acme:local-tool", "1.0.0");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[dev.dependencies]\n\"com.acme:local-tool\" = \"1.0.0\""));
        assertTrue(toml.contains("\"org.springframework.boot:spring-boot-devtools\" = {}"));
        assertEquals("1.0.0", parsed.devDependencies().get("com.acme:local-tool"));
        assertTrue(parsed.managedDevDependencies().contains("org.springframework.boot:spring-boot-devtools"));
    }

    @Test
    void addsDependenciesToCorrectSections() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main");
        config = writer.addDependency(config, DependencySection.MAIN, "com.google.guava:guava", "33.4.0-jre");
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals("33.4.0-jre", parsed.dependencies().get("com.google.guava:guava"));
        assertEquals("5.11.4", parsed.testDependencies().get("org.junit.jupiter:junit-jupiter"));
    }
}
