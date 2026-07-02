package sh.zolt.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class DependencyMetadataTest {
    @Test
    void normalizesBlankOptionalFields() {
        DependencyMetadata metadata = new DependencyMetadata(
                "dependencies",
                "com.example:app",
                " ",
                " ",
                false,
                " ",
                false,
                false,
                null);

        assertEquals("dependencies|com.example:app", DependencyMetadata.key("dependencies", "com.example:app"));
        assertTrue(metadata.emptyMetadata());
        assertEquals(List.of(), metadata.exclusions());
    }

    @Test
    void retainsPublishMetadataAndExclusions() {
        DependencyMetadata metadata = new DependencyMetadata(
                "dependencies",
                "com.example:app",
                "1.0.0",
                null,
                false,
                null,
                true,
                true,
                List.of(new DependencyExclusionSpec("com.example", "legacy")));

        assertFalse(metadata.emptyMetadata());
        assertEquals("com.example:legacy", metadata.exclusions().getFirst().coordinate());
    }

    @Test
    void requiresSectionAndCoordinate() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DependencyMetadata(null, "com.example:app", null, false, null, false, false, List.of()));

        assertEquals("Dependency metadata section and coordinate are required.", exception.getMessage());
    }
}
