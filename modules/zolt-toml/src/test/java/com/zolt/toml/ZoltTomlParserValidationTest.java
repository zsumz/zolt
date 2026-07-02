package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ZoltTomlParserValidationTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void rejectsCompilerOwnedJavacArgs() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "bad-compiler-args"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [compiler]
                args = ["--release", "17"]
                """));

        assertTrue(exception.getMessage().contains("Zolt owns `--release`"));
        assertTrue(exception.getMessage().contains("[compiler].release"));
    }

    @Test
    void rejectsMalformedJavaTestSourceRoots() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [test.sources]
                        java = "src/test/java"
                        """));

        assertEquals(
                "Invalid value for [test.sources].java in zolt.toml. Use an array of strings.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownTestSourceLanguage() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [test.sources]
                        kotlin = ["src/test/kotlin"]
                        """));

        assertTrue(exception.getMessage().contains("Unsupported Kotlin test source roots"));
        assertTrue(exception.getMessage().contains("public beta"));
    }

    @Test
    void rejectsUnsupportedSourceRootLanguage() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [build]
                        source = "src/main/scala"
                        """));

        assertTrue(exception.getMessage().contains("Unsupported Scala source root"));
        assertTrue(exception.getMessage().contains("src/main/scala"));
    }

    @Test
    void rejectsEmptyMainSourceRoots() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [build]
                        sources = []
                        """));

        assertTrue(exception.getMessage().contains("[build].sources"));
        assertTrue(exception.getMessage().contains("non-empty array"));
    }

    @Test
    void rejectsMismatchedPrimaryMainSourceRoot() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [build]
                        source = "src/main/java"
                        sources = ["src/generated/java", "src/main/java"]
                        """));

        assertTrue(exception.getMessage().contains("[build].source"));
        assertTrue(exception.getMessage().contains("first [build].sources entry"));
    }

    @Test
    void rejectsUnsafeOutputRoot() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [build]
                        outputRoot = "../target"
                        """));

        assertTrue(exception.getMessage().contains("[build].outputRoot"));
        assertTrue(exception.getMessage().contains(".zolt/build"));
    }

    @Test
    void invalidTomlFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project
                        name = "broken"
                        """));

        assertTrue(exception.getMessage().contains("Could not parse zolt.toml."));
        assertTrue(exception.getMessage().contains("Fix the TOML syntax"));
    }

    @Test
    void missingRequiredProjectFieldIsActionable() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "missing-version"
                        group = "com.example"
                        java = "21"
                        """));

        assertEquals(
                "Missing required field [project].version in zolt.toml. Add a non-empty string value.",
                exception.getMessage());
    }

    @Test
    void unknownTopLevelSectionFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [plugins]
                        custom = "nope"
                        """));

        assertTrue(exception.getMessage().contains("Unsupported build plugin configuration [plugins]"));
        assertTrue(exception.getMessage().contains("does not execute Maven or Gradle plugins"));
    }

    @Test
    void androidTopLevelSectionFailsAsUnsupportedBetaShape() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [android]
                        namespace = "com.example"
                        """));

        assertTrue(exception.getMessage().contains("Unsupported Android configuration [android]"));
        assertTrue(exception.getMessage().contains("public beta"));
    }

    @Test
    void unknownProjectFieldFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"
                        packaging = "jar"
                        """));

        assertEquals(
                "Unknown field [project].packaging in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }
}
