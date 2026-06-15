package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.DependencyMetadata;
import com.zolt.project.ProjectConfig;
import org.junit.jupiter.api.Test;

final class ZoltTomlDependencyMetadataParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesDependencyMetadata() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "library"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.example:core" = { version = "1.0.0", optional = true, exclusions = [{ group = "com.example", artifact = "legacy-logging" }] }
                "com.example:publish-helper" = { version = "2.0.0", publishOnly = true }
                "com.example:managed-publish" = { publishOnly = true }
                """);

        DependencyMetadata core = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.example:core"));
        DependencyMetadata publish = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.example:publish-helper"));
        DependencyMetadata managedPublish = config.dependencyMetadata()
                .get(DependencyMetadata.key("dependencies", "com.example:managed-publish"));
        assertEquals("1.0.0", config.dependencies().get("com.example:core"));
        assertFalse(config.dependencies().containsKey("com.example:publish-helper"));
        assertFalse(config.managedDependencies().contains("com.example:managed-publish"));
        assertTrue(core.optional());
        assertFalse(core.publishOnly());
        assertEquals("com.example:legacy-logging", core.exclusions().getFirst().coordinate());
        assertEquals("2.0.0", publish.version());
        assertTrue(publish.publishOnly());
        assertTrue(managedPublish.managed());
        assertTrue(managedPublish.publishOnly());
    }

}
