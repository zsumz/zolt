package com.zolt.explain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenStaticProjectInspectorFormatterTest {
    @TempDir
    private Path tempDir;

    private final MavenStaticProjectInspector inspector = new MavenStaticProjectInspector();

    @Test
    void formatterEmitsDeterministicTextAndJson() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <artifactId>demo</artifactId>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        MavenExplainFormatter formatter = new MavenExplainFormatter();
        String text = formatter.text(result);
        String json = formatter.json(result);

        assertTrue(text.contains("Zolt explain: Maven project"));
        assertTrue(text.contains("Projects: 1"));
        assertTrue(text.contains("did not execute Maven"));
        assertTrue(json.startsWith("{\n  \"schemaVersion\": 1,"));
        assertTrue(json.contains("\"source\": \"maven\""));
        assertTrue(json.contains("\"projects\": ["));
        assertFalse(json.contains("\r"));
    }
}
