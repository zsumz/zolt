package sh.zolt.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class JavaPackageValidatorTest {
    @Test
    void acceptsJavaIdentifierPackageSegments() {
        assertEquals("sh.zolt.generated_1.$internal", JavaPackageValidator.requireValid(
                "[project].group",
                "sh.zolt.generated_1.$internal"));
    }

    @Test
    void rejectsMalformedPackageShapesWithActionablePattern() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JavaPackageValidator.requireValid("[project].group", "9bad.segment"));

        assertTrue(exception.getMessage().contains("Invalid [project].group Java package."));
        assertTrue(exception.getMessage().contains("[A-Za-z_$][A-Za-z0-9_$]*"));
    }

    @Test
    void rejectsReservedJavaKeywordSegments() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JavaPackageValidator.requireValid("[project].group", "sh.class.demo"));

        assertEquals(
                "Invalid [project].group Java package segment `class`. Use segment(.segment)* with Java identifier segments that are not Java keywords.",
                exception.getMessage());
    }
}
