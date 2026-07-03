package sh.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MavenExplainJsonFieldsTest {
    @Test
    void scalarFieldsEscapeJsonControlsAndCommas() {
        StringBuilder json = new StringBuilder();

        MavenExplainJsonFields.stringField(json, 1, "message", "quote \" slash \\ return\r form\f back\b", true);
        MavenExplainJsonFields.booleanField(json, 1, "active", true, true);
        MavenExplainJsonFields.intField(json, 1, "count", 3, false);

        assertEquals("""
                  "message": "quote \\" slash \\\\ return\\r form\\f back\\b",
                  "active": true,
                  "count": 3
                """, json.toString());
    }

    @Test
    void arrayFieldsPathAndCommaUseStableJsonFormatting() {
        StringBuilder json = new StringBuilder();

        MavenExplainJsonFields.stringArrayField(json, 2, "values", List.of("plain", "tab\tvalue"), false);
        MavenExplainJsonFields.comma(json);

        assertEquals("    \"values\": [\"plain\", \"tab\\tvalue\"]\n,\n", json.toString());
        assertEquals("C:/work/pom.xml", MavenExplainJsonFields.path(Path.of("C:\\work\\pom.xml")));
    }
}
