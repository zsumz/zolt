package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CurrentWorkerClasspathTest {
    private final CurrentWorkerClasspath currentWorkerClasspath = new CurrentWorkerClasspath();

    @Test
    void normalizesNonBlankClasspathEntries() {
        List<Path> result = currentWorkerClasspath.discover("lib/zolt.jar::target/classes", ":");

        assertEquals(List.of(
                Path.of("lib/zolt.jar").toAbsolutePath().normalize(),
                Path.of("target/classes").toAbsolutePath().normalize()), result);
    }

    @Test
    void emptyClasspathIsActionable() {
        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> currentWorkerClasspath.discover("", ":"));

        assertTrue(exception.getMessage().contains("Could not determine Zolt worker classpath"));
        assertTrue(exception.getMessage().contains("java.class.path"));
    }
}
