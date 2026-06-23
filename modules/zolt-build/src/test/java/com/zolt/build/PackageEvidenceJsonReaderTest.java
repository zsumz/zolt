package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PackageEvidenceJsonReaderTest {
    private final Path manifest = Path.of("target/demo.jar.zolt-package.json");

    @Test
    void readsScalarArrayAndObjectValues() {
        PackageEvidenceJsonReader reader = new PackageEvidenceJsonReader("""
                {
                  "name": "demo",
                  "entries": 12,
                  "target": null,
                  "sources": ["one", "two"],
                  "objects": [
                    {
                      "value": "contains { braces } and [brackets]"
                    }
                  ]
                }
                """, manifest);

        assertEquals("demo", reader.requiredString("name"));
        assertEquals(12, reader.requiredInt("entries"));
        assertEquals(Optional.empty(), reader.nullableString("target"));
        assertEquals(List.of("one", "two"), reader.stringArray("sources"));
        assertEquals(
                "contains { braces } and [brackets]",
                reader.objectArray("objects").get(0).requiredString("value"));
    }

    @Test
    void decodesEscapedStringValues() {
        PackageEvidenceJsonReader reader = new PackageEvidenceJsonReader("""
                {
                  "value": "quote: \\" slash: \\\\ newline: \\n tab: \\t"
                }
                """, manifest);

        assertEquals("quote: \" slash: \\ newline: \n tab: \t", reader.requiredString("value"));
    }

    @Test
    void malformedValuesUsePackageEvidenceRegenerationDiagnostic() {
        PackageEvidenceJsonReader reader = new PackageEvidenceJsonReader("""
                {
                  "sources": "not-an-array"
                }
                """, manifest);

        PackageException exception = assertThrows(PackageException.class, () -> reader.stringArray("sources"));

        assertTrue(exception.getMessage().contains("is missing string field `sources`"));
        assertTrue(exception.getMessage().contains("Regenerate package evidence with `zolt package`."));
    }
}
