package com.zolt.classpath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ClasspathFormatterTest {
    @Test
    void formatsEntriesWithConfiguredPathSeparator() {
        String output = new ClasspathFormatter(":").format(new Classpath(List.of(
                Path.of("alpha.jar"),
                Path.of("zeta.jar"))));

        assertEquals("alpha.jar:zeta.jar" + System.lineSeparator(), output);
    }

    @Test
    void formatsEmptyClasspathAsBlankLine() {
        String output = new ClasspathFormatter(":").format(new Classpath(List.of()));

        assertEquals(System.lineSeparator(), output);
    }
}
