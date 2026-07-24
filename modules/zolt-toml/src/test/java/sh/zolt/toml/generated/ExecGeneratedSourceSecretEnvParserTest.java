package sh.zolt.toml.generated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.ZoltTomlParser;
import org.junit.jupiter.api.Test;

/**
 * Hole 2 parser contract: a step with {@code secretEnv} may not use the default {@code cache = "content"}
 * (Zolt never reads a secret's value into the fingerprint), and the two honest escapes — {@code cache =
 * "none"} or an explicit non-secret {@code cacheSalt} — are accepted.
 */
final class ExecGeneratedSourceSecretEnvParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void rejectsSecretEnvWithContentCacheAndNoSalt() {
        ZoltConfigException error = assertThrows(ZoltConfigException.class,
                () -> parser.parse(secretEnvConfig("", "")));
        assertTrue(error.getMessage().contains("secretEnv with cache = \"content\""), error.getMessage());
        assertTrue(error.getMessage().contains("cache = \"none\""), error.getMessage());
        assertTrue(error.getMessage().contains("cacheSalt"), error.getMessage());
    }

    @Test
    void acceptsSecretEnvWhenCacheIsNone() {
        ProjectConfig config = parser.parse(secretEnvConfig("cache = \"none\"", ""));
        GeneratedSourceStep step = config.build().generatedMainSources().getFirst();
        assertEquals("none", step.exec().cache());
        assertEquals("SRC_TOKEN", step.exec().secretEnv().get("TOKEN"));
        assertTrue(step.exec().cacheSalt().isEmpty());
    }

    @Test
    void acceptsSecretEnvWithContentCacheWhenCacheSaltPresent() {
        ProjectConfig config = parser.parse(secretEnvConfig("", "cacheSalt = \"rev-7\""));
        GeneratedSourceStep step = config.build().generatedMainSources().getFirst();
        assertEquals("content", step.exec().cache());
        assertEquals("rev-7", step.exec().cacheSalt().orElseThrow());
    }

    private static String secretEnvConfig(String cacheLine, String cacheSaltLine) {
        return """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                jooq = "3.19.15"

                [generated.execTools.jooq]
                runner = "jvm"
                coordinates = [{ coordinate = "org.jooq:jooq-codegen", versionRef = "jooq" }]
                mainClass = "org.jooq.codegen.GenerationTool"

                [generated.main.model]
                kind = "exec"
                tool = "jooq"
                inputs = ["src/main/jooq/config.xml"]
                output = "target/generated/sources/jooq"
                produces = "java-sources"
                %s
                %s
                [generated.main.model.secretEnv]
                TOKEN = "SRC_TOKEN"
                """.formatted(cacheLine, cacheSaltLine);
    }
}
