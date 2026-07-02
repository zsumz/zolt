package sh.zolt.explain.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GradleStaticProjectInspectorFormatterTest {
    @TempDir
    private Path tempDir;

    private final GradleStaticProjectInspector inspector = new GradleStaticProjectInspector();

    @Test
    void formatterEmitsDeterministicTextAndJson() throws IOException {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }\n");

        GradleInspectionResult result = inspector.inspect(tempDir);
        GradleExplainFormatter formatter = new GradleExplainFormatter();
        String text = formatter.text(result);
        String json = formatter.json(result);

        assertTrue(text.contains("Zolt explain: Gradle project"));
        assertTrue(text.contains("Projects: 1"));
        assertTrue(text.contains("did not execute Gradle"));
        assertTrue(json.startsWith("{\n  \"schemaVersion\": 1,"));
        assertTrue(json.contains("\"source\": \"gradle\""));
        assertTrue(json.contains("\"projects\": ["));
        assertFalse(json.contains("\r"));
    }
}
