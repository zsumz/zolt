package sh.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.ProjectConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ZoltTomlNativeSettingsParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesNativeSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [native]
                imageName = "demo-native"
                output = "target/native-custom"
                args = ["--no-fallback", "--native-image-info"]
                """);

        assertEquals("demo-native", config.nativeSettings().imageName());
        assertEquals("target/native-custom", config.nativeSettings().output());
        assertEquals(
                List.of("--no-fallback", "--native-image-info"),
                config.nativeSettings().args());
    }

    @Test
    void defaultsNativeImageNameWhenNativeSectionIsPresent() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [native]
                output = "target/native-custom"
                """);

        assertEquals("demo", config.nativeSettings().imageName());
        assertEquals("target/native-custom", config.nativeSettings().output());
        assertTrue(config.nativeSettings().args().isEmpty());
    }

    @Test
    void unknownNativeFieldFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [native]
                        script = "native-image.sh"
                        """));

        assertEquals(
                "Unknown field [native].script in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void nativeArgsMustBeNonEmptyStrings() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [native]
                        args = ["--no-fallback", ""]
                        """));

        assertEquals(
                "Invalid value for [native].args[1] in zolt.toml. Use a non-empty string.",
                exception.getMessage());
    }

    @Test
    void nativeOutputMustBeAString() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [native]
                        output = 123
                        """));

        assertEquals(
                "Invalid value for [native].output in zolt.toml. Use a non-empty string value.",
                exception.getMessage());
    }
}
