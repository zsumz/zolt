package com.zolt.toml.testsection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import org.junit.jupiter.api.Test;

final class ZoltTomlTestSuitesParserValidationTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void rejectsReservedAllTestSuiteName() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [test.suites.all]
                        includeClassname = ["*Test"]
                        """));

        assertTrue(exception.getMessage().contains("`all` is reserved"));
    }

    @Test
    void rejectsUnknownTestSuiteField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [test.suites.fast]
                        include = ["*Test"]
                        """));

        assertEquals(
                "Unknown field [test.suites.fast].include in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedTestSuitePattern() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [test.suites.fast]
                        includeClassname = ["bad pattern"]
                        """));

        assertTrue(exception.getMessage().contains("Patterns must not contain whitespace"));
    }

    @Test
    void rejectsInvalidTestSuiteMaxWorkers() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [test.suites.fast]
                        includeClassname = ["*Test"]
                        maxWorkers = 0
                        """));

        assertEquals("test.suites.maxWorkers must be greater than zero.", exception.getMessage());
    }

    @Test
    void rejectsMalformedTestSuiteResourceLocks() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [test.suites.fast]
                        includeClassname = ["*Test"]
                        resourceLocks = { "com.example.DbTest" = "database" }
                        """));

        assertEquals(
                "Invalid value for [test.suites.fast.resourceLocks].com.example.DbTest in zolt.toml. Use an array of strings.",
                exception.getMessage());
    }
}
