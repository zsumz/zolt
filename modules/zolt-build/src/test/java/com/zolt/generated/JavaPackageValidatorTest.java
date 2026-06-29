package com.zolt.generated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.JavaPackageValidator;
import org.junit.jupiter.api.Test;

final class JavaPackageValidatorTest {
    @Test
    void acceptsValidJavaPackages() {
        assertEquals("com.example", JavaPackageValidator.requireValid("generated source", "com.example"));
        assertEquals("com.example_$._internal", JavaPackageValidator.requireValid("generated source", "com.example_$._internal"));
        assertEquals("a.b1.C_$", JavaPackageValidator.requireValid("generated source", "a.b1.C_$"));
    }

    @Test
    void rejectsPathAndInjectionShapes() {
        for (String packageName : new String[] {
            ".tmp.pwn",
            "../pwn",
            "com..example",
            "com.example; class Evil {}",
            "com.example\nclass Evil {}"
        }) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> JavaPackageValidator.requireValid("generated source", packageName));

            assertTrue(exception.getMessage().contains("generated source Java package"));
            assertTrue(exception.getMessage().contains("[A-Za-z_$][A-Za-z0-9_$]*"));
            assertTrue(!exception.getMessage().contains("Evil"), exception.getMessage());
        }
    }

    @Test
    void rejectsJavaKeywordSegments() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JavaPackageValidator.requireValid("generated source", "com.class.example"));

        assertTrue(exception.getMessage().contains("segment `class`"));
        assertTrue(exception.getMessage().contains("not Java keywords"));
    }
}
