package sh.zolt.explain.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GradleExplainJsonFieldsTest {
    @Test
    void stringAndArrayFieldsEscapeJsonControlsAndCommas() {
        StringBuilder json = new StringBuilder();

        GradleExplainJsonFields.stringField(
                json,
                1,
                "message",
                "quote \" slash \\ newline\n tab\t ctrl " + '\u0001',
                true);
        GradleExplainJsonFields.stringArrayField(json, 1, "values", List.of("plain", "line\nbreak"), false);

        assertEquals("""
                  "message": "quote \\" slash \\\\ newline\\n tab\\t ctrl \\u0001",
                  "values": ["plain", "line\\nbreak"]
                """, json.toString());
    }

    @Test
    void intFieldsPathAndCommaUseStableJsonFormatting() {
        StringBuilder json = new StringBuilder();

        GradleExplainJsonFields.intField(json, 2, "count", 7, false);
        GradleExplainJsonFields.comma(json);

        assertEquals("    \"count\": 7\n,\n", json.toString());
        assertEquals("C:/work/app", GradleExplainJsonFields.path(Path.of("C:\\work\\app")));
    }
}
