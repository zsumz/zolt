package com.zolt.build.testruntime.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.framework.FrameworkTestSelection;
import com.zolt.test.TestSelection;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FrameworkTestSelectionMapperTest {
    private final FrameworkTestSelectionMapper mapper = new FrameworkTestSelectionMapper();

    @Test
    void nullSelectionMapsToEmptyFrameworkSelection() {
        FrameworkTestSelection selection = mapper.map(null);

        assertEquals(FrameworkTestSelection.empty(), selection);
    }

    @Test
    void mapsClassMethodsPatternsAndTags() {
        TestSelection selection = TestSelection.fromCli(
                List.of("com.example.FastTest", "com.example.FastTest#runs"),
                List.of("*IT"),
                List.of("fast"),
                List.of("slow"));

        FrameworkTestSelection result = mapper.map(selection);

        assertEquals(List.of("com.example.FastTest"), result.classSelectors());
        assertEquals("com.example.FastTest", result.methodSelectors().getFirst().className());
        assertEquals("runs", result.methodSelectors().getFirst().methodName());
        assertEquals(selection.classNamePatterns(), result.classNamePatterns());
        assertEquals(List.of("fast"), result.includedTags());
        assertEquals(List.of("slow"), result.excludedTags());
    }
}
