package sh.zolt.toml.generated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.ExecGenerationSettings;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProducesLane;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.ZoltTomlParser;
import sh.zolt.toml.ZoltTomlWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ExecGeneratedSourceParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    private static final String JOOQ_CONFIG = """
            [project]
            name = "demo"
            version = "0.1.0"
            group = "com.example"
            java = "21"

            [versions]
            jooq = "3.19.15"
            postgres = "42.7.4"

            [generated.execTools.jooq]
            runner = "jvm"
            coordinates = [
                { coordinate = "org.jooq:jooq-codegen", versionRef = "jooq" },
                { coordinate = "org.postgresql:postgresql", version = "42.7.4" },
            ]
            mainClass = "org.jooq.codegen.GenerationTool"

            [generated.main.jooq-model]
            kind = "exec"
            tool = "jooq"
            args = ["src/main/jooq/config.xml"]
            inputs = ["src/main/jooq/config.xml", "src/main/resources/db/schema.sql"]
            output = "target/generated/sources/jooq"
            produces = "java-sources"

            [generated.main.assets]
            kind = "exec"
            tool = "jooq"
            inputs = ["src/main/assets/logo.svg"]
            output = "target/generated/assets"
            produces = "resources"
            into = "static"
            [generated.main.assets.env]
            ZOLT_MODE = "release"
            """;

    @Test
    void parsesExecToolsAndSteps() {
        ProjectConfig config = parser.parse(JOOQ_CONFIG);
        List<GeneratedSourceStep> steps = config.build().generatedMainSources();
        assertEquals(2, steps.size());

        GeneratedSourceStep model = steps.stream().filter(step -> step.id().equals("jooq-model")).findFirst().orElseThrow();
        assertEquals(GeneratedSourceKind.EXEC, model.kind());
        ExecGenerationSettings exec = model.exec();
        assertEquals("jooq", exec.toolName());
        assertEquals("jvm", exec.tool().runner());
        assertEquals("org.jooq.codegen.GenerationTool", exec.tool().mainClass());
        assertEquals(2, exec.tool().coordinates().size());
        assertEquals("org.jooq:jooq-codegen", exec.tool().coordinates().get(0).coordinate());
        assertEquals("3.19.15", exec.tool().coordinates().get(0).version().orElseThrow());
        assertEquals("jooq", exec.tool().coordinates().get(0).versionRef().orElseThrow());
        assertEquals("42.7.4", exec.tool().coordinates().get(1).version().orElseThrow());
        assertEquals(List.of("src/main/jooq/config.xml"), exec.args());
        assertEquals(ProducesLane.JAVA_SOURCES, exec.produces());
        assertEquals("content", exec.cache());

        GeneratedSourceStep assets = steps.stream().filter(step -> step.id().equals("assets")).findFirst().orElseThrow();
        assertEquals(ProducesLane.RESOURCES, assets.exec().produces());
        assertEquals("static", assets.exec().into().orElseThrow());
        assertEquals("release", assets.exec().env().get("ZOLT_MODE"));
    }

    @Test
    void roundTripsExecToolsAndStepsThroughWriter() {
        ProjectConfig config = parser.parse(JOOQ_CONFIG);
        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[generated.execTools.jooq]\n"), toml);
        assertTrue(toml.contains("runner = \"jvm\""), toml);
        assertTrue(toml.contains("{ coordinate = \"org.jooq:jooq-codegen\", versionRef = \"jooq\" }"), toml);
        assertTrue(toml.contains("mainClass = \"org.jooq.codegen.GenerationTool\""), toml);
        assertTrue(toml.contains("[generated.main.jooq-model]\n"), toml);
        assertTrue(toml.contains("kind = \"exec\""), toml);
        assertTrue(toml.contains("tool = \"jooq\""), toml);
        assertTrue(toml.contains("produces = \"java-sources\""), toml);
        assertTrue(toml.contains("into = \"static\""), toml);
        assertEquals(sortedById(config.build().generatedMainSources()), sortedById(parsed.build().generatedMainSources()));
        assertEquals(config.versionAliases(), parsed.versionAliases());
    }

    private static List<GeneratedSourceStep> sortedById(List<GeneratedSourceStep> steps) {
        return steps.stream().sorted(java.util.Comparator.comparing(GeneratedSourceStep::id)).toList();
    }

    @Test
    void rejectsUnknownToolReference() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.step]
                kind = "exec"
                tool = "missing"
                inputs = ["src/main/x"]
                output = "target/generated/x"
                produces = "java-sources"
                """));
        assertTrue(exception.getMessage().contains("Unknown exec tool `missing`"), exception.getMessage());
    }

    private static final String FRONTEND_CONFIG = """
            [project]
            name = "demo"
            version = "0.1.0"
            group = "com.example"
            java = "21"

            [generated.execTools.node]
            runner = "process"
            binary = "npm"
            versionCommand = ["npm", "--version"]
            versionExpect = ">=10 <11"
            allowUnpinnedTool = true

            [generated.main.frontend-install]
            kind = "exec"
            tool = "node"
            cwd = "web"
            args = ["ci"]
            inputs = ["web/package.json", "web/package-lock.json"]
            output = "web/node_modules"
            produces = "intermediate"
            timeoutSeconds = 120

            [generated.main.frontend-build]
            kind = "exec"
            tool = "node"
            cwd = "web"
            args = ["run", "build"]
            inputs = ["web/package-lock.json", "web/node_modules", "web/src/main.ts"]
            output = "web/dist"
            produces = "resources"
            into = "static"
            inheritEnv = ["CI"]
            cacheSalt = "frontend-build-1"
            [generated.main.frontend-build.env]
            NODE_ENV = "production"
            [generated.main.frontend-build.secretEnv]
            NPM_TOKEN = "CI_NPM_TOKEN"
            """;

    @Test
    void parsesProcessToolAndLanes() {
        ProjectConfig config = parser.parse(FRONTEND_CONFIG);
        List<GeneratedSourceStep> steps = config.build().generatedMainSources();

        GeneratedSourceStep install = steps.stream().filter(step -> step.id().equals("frontend-install")).findFirst().orElseThrow();
        assertEquals("process", install.exec().tool().runner());
        assertEquals("npm", install.exec().tool().binary());
        assertEquals(List.of("npm", "--version"), install.exec().tool().versionCommand());
        assertEquals(">=10 <11", install.exec().tool().versionExpect().orElseThrow());
        assertTrue(install.exec().tool().allowUnpinnedTool());
        assertEquals(ProducesLane.INTERMEDIATE, install.exec().produces());
        assertEquals("web", install.exec().cwd().orElseThrow());
        assertEquals(120, install.exec().timeoutSeconds());

        GeneratedSourceStep build = steps.stream().filter(step -> step.id().equals("frontend-build")).findFirst().orElseThrow();
        assertEquals(ProducesLane.RESOURCES, build.exec().produces());
        assertEquals("static", build.exec().into().orElseThrow());
        assertEquals(List.of("CI"), build.exec().inheritEnv());
        assertEquals("production", build.exec().env().get("NODE_ENV"));
        assertEquals("CI_NPM_TOKEN", build.exec().secretEnv().get("NPM_TOKEN"));
        assertEquals("frontend-build-1", build.exec().cacheSalt().orElseThrow());
    }

    @Test
    void roundTripsProcessToolAndLanesThroughWriter() {
        ProjectConfig config = parser.parse(FRONTEND_CONFIG);
        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("runner = \"process\""), toml);
        assertTrue(toml.contains("binary = \"npm\""), toml);
        assertTrue(toml.contains("versionCommand = [\"npm\", \"--version\"]"), toml);
        assertTrue(toml.contains("allowUnpinnedTool = true"), toml);
        assertTrue(toml.contains("produces = \"intermediate\""), toml);
        assertTrue(toml.contains("cwd = \"web\""), toml);
        assertTrue(toml.contains("timeoutSeconds = 120"), toml);
        assertTrue(toml.contains("inheritEnv = [\"CI\"]"), toml);
        assertTrue(toml.contains("secretEnv = { \"NPM_TOKEN\" = \"CI_NPM_TOKEN\" }"), toml);
        assertEquals(sortedById(config.build().generatedMainSources()), sortedById(parsed.build().generatedMainSources()));
    }

    @Test
    void parsesAndRoundTripsProjectPseudoTool() {
        String projectConfig = """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.assets]
                kind = "exec"
                tool = "project"
                mainClass = "com.example.AssetGenerator"
                inputs = ["target/classes"]
                output = "target/generated/assets"
                produces = "resources"
                cache = "none"
                """;
        ProjectConfig config = parser.parse(projectConfig);
        GeneratedSourceStep step = config.build().generatedMainSources().getFirst();
        assertEquals("project", step.exec().toolName());
        assertEquals("project", step.exec().tool().runner());
        assertEquals("com.example.AssetGenerator", step.exec().tool().mainClass());
        assertEquals("none", step.exec().cache());

        String toml = writer.write(config);
        assertTrue(toml.contains("tool = \"project\""), toml);
        assertTrue(toml.contains("mainClass = \"com.example.AssetGenerator\""), toml);
        assertTrue(toml.contains("cache = \"none\""), toml);
        // the built-in project pseudo-tool is never emitted as a declared execTools table.
        assertFalse(toml.contains("[generated.execTools.project]"), toml);
        ProjectConfig parsed = parser.parse(toml);
        assertEquals(sortedById(config.build().generatedMainSources()), sortedById(parsed.build().generatedMainSources()));
    }

    @Test
    void parsesTestResourcesLane() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.execTools.t]
                runner = "jvm"
                coordinates = [{ coordinate = "x:y", version = "1.0.0" }]
                mainClass = "M"

                [generated.test.fixtures]
                kind = "exec"
                tool = "t"
                inputs = ["src/test/fixtures/seed.txt"]
                output = "target/generated/test-fixtures"
                produces = "test-resources"
                into = "data"
                """);
        GeneratedSourceStep step = config.build().generatedTestSources().getFirst();
        assertEquals(ProducesLane.TEST_RESOURCES, step.exec().produces());
        assertEquals("data", step.exec().into().orElseThrow());
    }

    @Test
    void rejectsUnsupportedRunner() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.execTools.node]
                runner = "docker"
                binary = "docker"
                versionCommand = ["docker", "--version"]

                [generated.main.step]
                kind = "exec"
                tool = "node"
                inputs = ["web/package.json"]
                output = "web/node_modules"
                produces = "intermediate"
                """));
        assertTrue(exception.getMessage().contains("Supported runners are: jvm, process"), exception.getMessage());
    }

    @Test
    void rejectsProcessToolWithoutVersionCommand() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.execTools.node]
                runner = "process"
                binary = "npm"
                allowUnpinnedTool = true

                [generated.main.step]
                kind = "exec"
                tool = "node"
                inputs = ["web/package.json"]
                output = "web/node_modules"
                produces = "intermediate"
                """));
        assertTrue(exception.getMessage().contains("versionCommand"), exception.getMessage());
    }

    @Test
    void rejectsMainClassOnNonProjectTool() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse(execStepConfig(
                "produces = \"java-sources\"\nmainClass = \"com.example.Rogue\"")));
        assertTrue(exception.getMessage().contains("mainClass applies only to tool = \"project\""), exception.getMessage());
    }

    @Test
    void rejectsProjectToolWithoutMainClass() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.step]
                kind = "exec"
                tool = "project"
                inputs = ["target/classes"]
                output = "target/generated/x"
                produces = "resources"
                """));
        assertTrue(exception.getMessage().contains("mainClass"), exception.getMessage());
    }

    @Test
    void rejectsUnsupportedCachePolicy() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse(execStepConfig(
                "produces = \"java-sources\"\ncache = \"live-db\"")));
        assertTrue(exception.getMessage().contains("Supported cache policies are: content, none"), exception.getMessage());
    }

    @Test
    void rejectsIntoWithoutResourcesLane() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse(execStepConfig(
                "produces = \"java-sources\"\ninto = \"static\"")));
        assertTrue(exception.getMessage().contains("into applies only to produces = \"resources\""), exception.getMessage());
    }

    @Test
    void rejectsUnsupportedProducesLane() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse(execStepConfig(
                "produces = \"sideways\"")));
        assertTrue(exception.getMessage().contains("Unsupported exec produces lane `sideways`"), exception.getMessage());
    }

    @Test
    void rejectsNonPositiveTimeout() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse(execStepConfig(
                "produces = \"java-sources\"\ntimeoutSeconds = 0")));
        assertTrue(exception.getMessage().contains("timeoutSeconds"), exception.getMessage());
    }

    @Test
    void rejectsUnknownVersionRefOnCoordinate() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.execTools.t]
                runner = "jvm"
                coordinates = [{ coordinate = "x:y", versionRef = "missing" }]
                mainClass = "M"

                [generated.main.step]
                kind = "exec"
                tool = "t"
                inputs = ["src/main/x"]
                output = "target/generated/x"
                produces = "java-sources"
                """));
        assertTrue(exception.getMessage().contains("Unknown versionRef `missing`"), exception.getMessage());
    }

    private static String execStepConfig(String stepExtras) {
        return """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.execTools.t]
                runner = "jvm"
                coordinates = [{ coordinate = "x:y", version = "1.0.0" }]
                mainClass = "M"

                [generated.main.step]
                kind = "exec"
                tool = "t"
                inputs = ["src/main/x"]
                output = "target/generated/x"
                %s
                """.formatted(stepExtras);
    }
}
