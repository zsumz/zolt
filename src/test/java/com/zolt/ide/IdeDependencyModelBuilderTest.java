package com.zolt.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IdeDependencyModelBuilderTest {
    private final IdeDependencyModelBuilder builder = new IdeDependencyModelBuilder();

    @TempDir
    private Path tempDir;

    @Test
    void exportsDependencyDeclarationsWithVisibility() throws IOException {
        IdeModel.DependencyInfo dependencies = builder.build(parse("dependencies", """
                [project]
                name = "dependencies"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [platforms]
                "com.example:platform" = "1.0.0"

                [api.dependencies]
                "com.example:api-contract" = "1.0.0"
                "com.example:managed-api" = {}
                "com.example:workspace-api" = { workspace = "modules/api" }

                [dependencies]
                "com.example:impl" = { version = "1.0.0", optional = true, exclusions = [{ group = "com.example", artifact = "legacy-logging" }] }
                "com.example:publish-helper" = { version = "2.0.0", publishOnly = true }

                [runtime.dependencies]
                "com.example:runtime-only" = {}

                [provided.dependencies]
                "jakarta.servlet:jakarta.servlet-api" = "6.1.0"

                [dev.dependencies]
                "org.springframework.boot:spring-boot-devtools" = {}

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = {}

                [annotationProcessors]
                "com.example:processor" = "1.0.0"
                """));

        assertEquals(List.of(
                        new IdeModel.DependencyDeclaration("com.example:api-contract", "1.0.0", false, null),
                        new IdeModel.DependencyDeclaration("com.example:managed-api", null, true, null),
                        new IdeModel.DependencyDeclaration("com.example:workspace-api", null, false, "modules/api")),
                dependencies.api());
        assertEquals(
                List.of(
                        new IdeModel.DependencyDeclaration(
                                "com.example:impl",
                                "1.0.0",
                                false,
                                null,
                                true,
                                false,
                                List.of("com.example:legacy-logging")),
                        new IdeModel.DependencyDeclaration(
                                "com.example:publish-helper",
                                "2.0.0",
                                false,
                                null,
                                false,
                                true,
                                List.of())),
                dependencies.implementation());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration("com.example:runtime-only", null, true, null)),
                dependencies.runtime());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration("jakarta.servlet:jakarta.servlet-api", "6.1.0", false, null)),
                dependencies.provided());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration("org.springframework.boot:spring-boot-devtools", null, true, null)),
                dependencies.dev());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration("org.junit.jupiter:junit-jupiter", null, true, null)),
                dependencies.test());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration("com.example:processor", "1.0.0", false, null)),
                dependencies.annotationProcessors());

        String json = new IdeModelJsonWriter().write(modelWith(dependencies));
        assertTrue(json.contains("\"dependencies\": {"));
        assertTrue(json.contains("\"versionAliases\": {}"));
        assertTrue(json.contains("\"api\": ["));
        assertTrue(json.contains("\"implementation\": ["));
        assertTrue(json.contains("\"runtime\": ["));
        assertTrue(json.contains("\"provided\": ["));
        assertTrue(json.contains("\"dev\": ["));
        assertTrue(json.contains("\"coordinate\": \"com.example:workspace-api\""));
        assertTrue(json.contains("\"publishOnly\": true"));
        assertTrue(json.contains("\"exclusions\": ["));
        assertTrue(json.contains("\"workspace\": \"modules/api\""));
        assertTrue(json.contains("\"versionRef\": null"));
        assertTrue(json.contains("\"managed\": true"));
    }

    @Test
    void exportsVersionAliasesAndDependencyVersionRefs() throws IOException {
        IdeModel.DependencyInfo dependencies = builder.build(parse("alias-dependencies", """
                [project]
                name = "alias-dependencies"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                guava = "33.4.8-jre"
                junit = "5.12.1"

                [dependencies]
                "com.google.guava:guava" = { versionRef = "guava", optional = true }

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = { versionRef = "junit" }
                """));

        assertEquals(Map.of("guava", "33.4.8-jre", "junit", "5.12.1"), dependencies.versionAliases());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration(
                        "com.google.guava:guava",
                        "33.4.8-jre",
                        "guava",
                        false,
                        null,
                        true,
                        false,
                        List.of())),
                dependencies.implementation());
        assertEquals(
                List.of(new IdeModel.DependencyDeclaration(
                        "org.junit.jupiter:junit-jupiter",
                        "5.12.1",
                        "junit",
                        false,
                        null,
                        false,
                        false,
                        List.of())),
                dependencies.test());

        String json = new IdeModelJsonWriter().write(modelWith(dependencies));
        assertTrue(json.contains("\"versionAliases\": {"));
        assertTrue(json.contains("\"guava\": \"33.4.8-jre\""));
        assertTrue(json.contains("\"versionRef\": \"guava\""));
        assertTrue(json.contains("\"versionRef\": \"junit\""));
    }

    private ProjectConfig parse(String directoryName, String toml) throws IOException {
        Path projectDir = tempDir.resolve(directoryName);
        Files.createDirectories(projectDir);
        Path config = projectDir.resolve("zolt.toml");
        Files.writeString(config, toml);
        return new ZoltTomlParser().parse(config);
    }

    private IdeModel modelWith(IdeModel.DependencyInfo dependencies) {
        return new IdeModel(
                1,
                new IdeModel.ProjectInfo("dependencies", "com.example", "0.1.0", null),
                new IdeModel.JavaInfo("21", null, null),
                new IdeModel.CompilerInfo(null, null, null, List.of(), List.of(), null, null),
                new IdeModel.TestRuntimeInfo(List.of(), Map.of(), Map.of(), List.of()),
                new IdeModel.PackageInfo(
                        null,
                        false,
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        new IdeModel.PublicationInfo(null, null, null, null, List.of(), null, null),
                        Map.of()),
                new IdeModel.PathInfo(tempDir, tempDir.resolve("zolt.toml"), tempDir.resolve("zolt.lock")),
                List.of(),
                List.of(),
                List.of(),
                new IdeModel.OutputInfo(null, null, null),
                dependencies,
                new IdeModel.ClasspathInfo(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new IdeModel.FrameworkInfo(new IdeModel.QuarkusInfo(
                        false,
                        null,
                        "disabled",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of())),
                List.of());
    }
}
