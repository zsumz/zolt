package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArchitectureAllowlistSupportTest {
    @Test
    void readAllowlistSkipsBlankAndCommentLinesWhilePreservingOrder(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                # path|followUp

                modules/alpha/src/main/java/Alpha.java|
                modules/beta/src/main/java/Beta.java|
                """);

        Map<String, Entry> expected = new LinkedHashMap<>();
        expected.put(
                "modules/alpha/src/main/java/Alpha.java",
                new Entry("modules/alpha/src/main/java/Alpha.java", ""));
        expected.put(
                "modules/beta/src/main/java/Beta.java",
                new Entry("modules/beta/src/main/java/Beta.java", ""));

        Map<String, Entry> result = ArchitectureAllowlistSupport.readAllowlist(
                allowlist,
                ArchitectureAllowlistSupportTest::parseEntry,
                Entry::path,
                "Duplicate test allowlist entry: ");

        assertEquals(expected, result);
        assertEquals(List.copyOf(expected.keySet()), List.copyOf(result.keySet()));
    }

    @Test
    void readAllowlistRejectsDuplicatePathsWithOwnerMessage(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                modules/alpha/src/main/java/Alpha.java|
                modules/alpha/src/main/java/Alpha.java|
                """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ArchitectureAllowlistSupport.readAllowlist(
                        allowlist,
                        ArchitectureAllowlistSupportTest::parseEntry,
                        Entry::path,
                        "Duplicate test allowlist entry: "));

        assertEquals(
                "Duplicate test allowlist entry: modules/alpha/src/main/java/Alpha.java",
                exception.getMessage());
    }

    private static Optional<Entry> parseEntry(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Optional.empty();
        }
        String[] parts = trimmed.split("\\|", -1);
        return Optional.of(new Entry(parts[0], parts[1]));
    }

    private record Entry(String path, String followUp) {
    }
}
