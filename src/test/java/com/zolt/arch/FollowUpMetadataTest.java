package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FollowUpMetadataTest {
    private static final Path FOLLOW_UPS = Path.of("followUps");
    private static final int MODERN_FOLLOW_UP_MIN_ID = 503;
    private static final Set<String> VALID_STATUSES = Set.of("Open", "Done", "Blocked", "Implemented");
    private static final Pattern FOLLOW_UP_FILENAME = Pattern.compile("follow-up-(\\d{3})-.+\\.md");

    @Test
    void modernFollowUpsHaveMatchingTitleAndValidStatus() throws IOException {
        List<String> violations = new ArrayList<>();
        for (FollowUpFile followUp : modernFollowUps(FOLLOW_UPS)) {
            if (!followUp.title().startsWith("# follow-up-" + followUp.id() + " - ")) {
                violations.add(followUp.path() + " title must start with `# follow-up-" + followUp.id() + " - `");
            }
            if (followUp.status().isEmpty()) {
                violations.add(followUp.path() + " must declare `Status:` near the top of the file");
            } else if (!VALID_STATUSES.contains(followUp.status().orElseThrow())) {
                violations.add(followUp.path() + " has unsupported status `" + followUp.status().orElseThrow() + "`");
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "FollowUp metadata violations:\n" + describe(violations));
    }

    @Test
    void scannerIgnoresLegacyFollowUpsBelowModernThreshold(@TempDir Path tempDir) throws IOException {
        write(tempDir.resolve("-legacy.md"), """
                # : Legacy followUp
                """);
        write(tempDir.resolve("-modern.md"), """
                #  - Modern followUp

                Status: Done
                """);

        assertEquals(
                List.of(new FollowUpFile(
                        tempDir.resolve("-modern.md"),
                        "503",
                        "#  - Modern followUp",
                        Optional.of("Done"))),
                modernFollowUps(tempDir));
    }

    private static List<FollowUpFile> modernFollowUps(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("follow-up-"))
                    .map(FollowUpMetadataTest::followUpFile)
                    .flatMap(Optional::stream)
                    .filter(followUp -> Integer.parseInt(followUp.id()) >= MODERN_FOLLOW_UP_MIN_ID)
                    .sorted(Comparator.comparing(FollowUpFile::path))
                    .toList();
        }
    }

    private static Optional<FollowUpFile> followUpFile(Path path) {
        Matcher matcher = FOLLOW_UP_FILENAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(path);
            String title = lines.isEmpty() ? "" : lines.get(0);
            Optional<String> status = lines.stream()
                    .limit(5)
                    .filter(line -> line.startsWith("Status:"))
                    .map(line -> line.substring("Status:".length()).trim())
                    .findFirst();
            return Optional.of(new FollowUpFile(path, matcher.group(1), title, status));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read followUp " + path, exception);
        }
    }

    private static void write(Path path, String contents) throws IOException {
        Files.writeString(path, contents);
    }

    private static String describe(List<String> values) {
        StringBuilder description = new StringBuilder();
        for (String value : values) {
            description.append("- ").append(value).append('\n');
        }
        return description.toString();
    }

    private record FollowUpFile(Path path, String id, String title, Optional<String> status) {
    }
}
