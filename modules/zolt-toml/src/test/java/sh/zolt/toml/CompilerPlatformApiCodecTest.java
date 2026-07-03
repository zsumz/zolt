package sh.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.CompilerSettings;
import sh.zolt.project.ProjectConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CompilerPlatformApiCodecTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void parsesHostPlatformApiValues() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "host-mode"
                version = "0.1.0"
                group = "com.example"
                java = "8"

                [compiler]
                platformApi = "host"
                testPlatformApi = "release"
                """);

        assertEquals("host", config.compilerSettings().platformApi());
        assertEquals("release", config.compilerSettings().testPlatformApi());
        assertTrue(config.compilerSettings().mainHostPlatformApi());
    }

    @Test
    void defaultsPlatformApiToReleaseWhenAbsent() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "strict"
                version = "0.1.0"
                group = "com.example"
                java = "8"
                """);

        assertEquals(CompilerSettings.PLATFORM_API_RELEASE, config.compilerSettings().platformApi());
        assertEquals("", config.compilerSettings().testPlatformApi());
    }

    @Test
    void rejectsInvalidPlatformApiValue() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "bad"
                version = "0.1.0"
                group = "com.example"
                java = "8"

                [compiler]
                platformApi = "hosted"
                """));

        assertTrue(exception.getMessage().contains("[compiler].platformApi"));
        assertTrue(exception.getMessage().contains("hosted"));
    }

    @Test
    void rejectsInvalidTestPlatformApiValue() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "bad"
                version = "0.1.0"
                group = "com.example"
                java = "8"

                [compiler]
                testPlatformApi = "native"
                """));

        assertTrue(exception.getMessage().contains("[compiler].testPlatformApi"));
    }

    @Test
    void stillRejectsRawSourceTargetInArgs() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "bad"
                version = "0.1.0"
                group = "com.example"
                java = "8"

                [compiler]
                args = ["-source", "8"]
                """));

        assertTrue(exception.getMessage().contains("Zolt owns `-source`"));
    }

    @Test
    void stillRejectsRawTargetInTestArgs() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "bad"
                version = "0.1.0"
                group = "com.example"
                java = "8"

                [compiler]
                testArgs = ["-target", "8"]
                """));

        assertTrue(exception.getMessage().contains("Zolt owns `-target`"));
    }

    @Test
    void roundTripsHostPlatformApi() {
        ProjectConfig config = ProjectConfigFixture.config()
                .compilerSettings(new CompilerSettings(
                        "target/generated/sources/annotations",
                        "target/generated/test-sources/annotations",
                        "8",
                        "",
                        List.of(),
                        List.of(),
                        "host",
                        "host"))
                .build();

        String toml = writer.write(config);
        assertTrue(toml.contains("platformApi = \"host\""), toml);
        assertTrue(toml.contains("testPlatformApi = \"host\""), toml);

        ProjectConfig parsed = parser.parse(toml);
        assertEquals("host", parsed.compilerSettings().platformApi());
        assertEquals("host", parsed.compilerSettings().testPlatformApi());
    }

    @Test
    void doesNotWritePlatformApiWhenDefault() {
        ProjectConfig config = ProjectConfigFixture.config()
                .compilerSettings(new CompilerSettings(
                        "target/generated/sources/annotations",
                        "target/generated/test-sources/annotations",
                        "8",
                        "",
                        List.of(),
                        List.of()))
                .build();

        String toml = writer.write(config);
        assertTrue(!toml.contains("platformApi"), toml);
    }
}
