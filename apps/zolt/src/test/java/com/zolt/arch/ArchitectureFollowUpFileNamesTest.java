package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ArchitectureFollowUpFileNamesTest {
    @Test
    void numericIdReadsModernFollowUpFilenames() {
        assertEquals(
                Optional.of("917"),
                ArchitectureFollowUpFileNames.numericId(Path.of("-add-complexity-budget.md")));
        assertEquals(
                Optional.of("1000"),
                ArchitectureFollowUpFileNames.numericId(Path.of("-support-four-digit-followUp-ids.md")));
    }

    @Test
    void followUpIdKeepsZoltPrefix() {
        assertEquals(
                Optional.of(""),
                ArchitectureFollowUpFileNames.followUpId(Path.of("-support-four-digit-followUp-ids.md")));
    }

    @Test
    void parserRejectsNonFollowUpFilenames() {
        assertEquals(Optional.empty(), ArchitectureFollowUpFileNames.numericId(Path.of("README.md")));
        assertEquals(Optional.empty(), ArchitectureFollowUpFileNames.numericId(Path.of("-too-short.md")));
        assertEquals(Optional.empty(), ArchitectureFollowUpFileNames.numericId(Path.of(".md")));
    }

    @Test
    void numberSortsPrefixedAndNumericIds() {
        assertEquals(
                List.of("099", "", ""),
                List.of("", "099", "").stream()
                        .sorted(Comparator.comparingInt(ArchitectureFollowUpFileNames::number))
                        .toList());
    }
}
