package sh.zolt.toml.generated;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void rejectsNonJvmRunner() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.execTools.node]
                runner = "process"
                coordinates = [{ coordinate = "x:y", version = "1" }]
                mainClass = "M"

                [generated.main.step]
                kind = "exec"
                tool = "node"
                inputs = ["web/package.json"]
                output = "web/node_modules"
                produces = "resources"
                """));
        assertTrue(exception.getMessage().contains("runner = \"jvm\""), exception.getMessage());
    }

    @Test
    void rejectsNonContentCachePolicy() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse(execStepConfig(
                "produces = \"java-sources\"\ncache = \"none\"")));
        assertTrue(exception.getMessage().contains("cache = \"content\""), exception.getMessage());
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
                "produces = \"intermediate\"")));
        assertTrue(exception.getMessage().contains("Unsupported exec produces lane `intermediate`"), exception.getMessage());
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
