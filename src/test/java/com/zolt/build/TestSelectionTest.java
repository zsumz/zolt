package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class TestSelectionTest {
    @Test
    void parsesClassMethodPatternsAndTags() {
        TestSelection selection = TestSelection.fromCli(
                List.of("com.example.UserServiceTest", "com.example.UserServiceTest#createsUser"),
                List.of("*ServiceTest"),
                List.of("fast", "unit"),
                List.of("slow"));

        assertEquals(List.of("com.example.UserServiceTest"), selection.classSelectors());
        assertEquals(1, selection.methodSelectors().size());
        assertEquals("com.example.UserServiceTest", selection.methodSelectors().getFirst().className());
        assertEquals("createsUser", selection.methodSelectors().getFirst().methodName());
        assertEquals(List.of("*ServiceTest"), selection.classNamePatterns());
        assertEquals(List.of("fast", "unit"), selection.includedTags());
        assertEquals(List.of("slow"), selection.excludedTags());
        assertEquals(3, selection.explicitSelectorCount());
        assertEquals(3, selection.tagSelectorCount());
    }

    @Test
    void emptySelectionIsEmpty() {
        TestSelection selection = TestSelection.fromCli(List.of(), List.of(), List.of(), List.of());

        assertTrue(selection.emptySelection());
        assertEquals(0, selection.explicitSelectorCount());
        assertEquals(0, selection.tagSelectorCount());
    }

    @Test
    void rejectsPatternInSingleTestSelector() {
        TestSelectionException exception = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromCli(List.of("*ServiceTest"), List.of(), List.of(), List.of()));

        assertTrue(exception.getMessage().contains("Use --tests for class-name patterns"));
    }

    @Test
    void rejectsMalformedMethodSelector() {
        TestSelectionException exception = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromCli(List.of("com.example.UserServiceTest#"), List.of(), List.of(), List.of()));

        assertTrue(exception.getMessage().contains("Use com.example.UserServiceTest#methodName"));
    }

    @Test
    void rejectsWhitespaceInTags() {
        TestSelectionException exception = assertThrows(
                TestSelectionException.class,
                () -> TestSelection.fromCli(List.of(), List.of(), List.of("slow tests"), List.of()));

        assertTrue(exception.getMessage().contains("Tags must not contain whitespace"));
    }

    @Test
    void convertsClassNamePatternsToRegexes() {
        TestSelection selection = TestSelection.fromCli(
                List.of(),
                List.of("*ServiceTest", "com.example.?serTest"),
                List.of(),
                List.of());

        assertEquals(List.of(".*ServiceTest", "com\\.example\\..serTest"), selection.classNameRegexPatterns());
    }
}
