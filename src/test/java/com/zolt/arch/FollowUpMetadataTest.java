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
    private static final List<String> REQUIRED_SECTIONS = List.of(
            "Goal",
            "Context",
            "Scope",
            "Acceptance",
            "Verification");
    private static final Pattern FOLLOW_UP_FILENAME = Pattern.compile("follow-up-(\\d{3})-.+\\.md");

    @Test
    void modernFollowUpsHaveMatchingTitleAndValidStatus() throws IOException {
        List<FollowUpFile> followUps = modernFollowUps(FOLLOW_UPS);
        List<String> violations = violations(followUps);
        violations.addAll(sequenceViolations(followUps));

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
                        Optional.of("Done"),
                        List.of(
                                "#  - Modern followUp",
                                "",
                                "Status: Done"))),
                modernFollowUps(tempDir));
    }

    @Test
    void doneFollowUpsCannotKeepUncheckedChecklistItems(@TempDir Path tempDir) throws IOException {
        write(tempDir.resolve("-open.md"), """
                #  - Open followUp

                Status: Open

                ## Goal

                Keep work visible.

                ## Context

                Work is still in progress.

                ## Scope

                Track a remaining item.

                ## Acceptance

                - [ ] Work in progress

                ## Verification

                - [ ] Later
                """);
        write(tempDir.resolve("-done.md"), """
                #  - Done followUp

                Status: Done

                ## Goal

                Complete work.

                ## Context

                Work is complete.

                ## Scope

                Track the completed item.

                ## Acceptance

                - [x] Complete

                ## Verification

                - [x] Checked
                """);
        write(tempDir.resolve("-stale.md"), """
                #  - Stale followUp

                Status: Done

                ## Goal

                Complete work.

                ## Context

                Work is not complete.

                ## Scope

                Track stale state.

                ## Acceptance

                - [ ] Incomplete

                ## Verification

                - [x] Checked
                """);

        List<FollowUpFile> followUps = modernFollowUps(tempDir);

        assertEquals(3, followUps.size());
        assertEquals(
                List.of(tempDir.resolve("-stale.md") + " is Done but still has unchecked checklist items"),
                violations(followUps));
    }

    @Test
    void modernFollowUpIdsMustBeContiguous(@TempDir Path tempDir) throws IOException {
        writeModernFollowUp(tempDir.resolve("-first.md"), "503");
        writeModernFollowUp(tempDir.resolve("-duplicate.md"), "503");
        writeModernFollowUp(tempDir.resolve("-second.md"), "504");
        writeModernFollowUp(tempDir.resolve("-gap.md"), "506");
        write(tempDir.resolve("-legacy.md"), """
                # : Legacy followUp
                """);

        assertEquals(
                List.of(
                        "Duplicate modern followUp id ",
                        "Missing modern followUp id  between  and "),
                sequenceViolations(modernFollowUps(tempDir)));
    }

    private static List<String> violations(List<FollowUpFile> followUps) {
        List<String> violations = new ArrayList<>();
        for (FollowUpFile followUp : followUps) {
            if (!followUp.title().startsWith("# follow-up-" + followUp.id() + " - ")) {
                violations.add(followUp.path() + " title must start with `# follow-up-" + followUp.id() + " - `");
            }
            if (followUp.status().isEmpty()) {
                violations.add(followUp.path() + " must declare `Status:` near the top of the file");
            } else if (!VALID_STATUSES.contains(followUp.status().orElseThrow())) {
                violations.add(followUp.path() + " has unsupported status `" + followUp.status().orElseThrow() + "`");
            }
            if (followUp.status().filter("Done"::equals).isPresent() && followUp.hasUncheckedChecklistItem()) {
                violations.add(followUp.path() + " is Done but still has unchecked checklist items");
            }
            for (String section : REQUIRED_SECTIONS) {
                if (!followUp.hasSection(section)) {
                    violations.add(followUp.path() + " is missing `## " + section + "`");
                }
            }
        }
        return violations;
    }

    @Test
    void modernFollowUpsRequireCoreSections(@TempDir Path tempDir) throws IOException {
        write(tempDir.resolve("-incomplete.md"), """
                #  - Incomplete followUp

                Status: Open

                ## Goal

                Keep enough context to start.

                ## Context

                Missing several core sections.
                """);

        assertEquals(
                List.of(
                        tempDir.resolve("-incomplete.md") + " is missing `## Scope`",
                        tempDir.resolve("-incomplete.md") + " is missing `## Acceptance`",
                        tempDir.resolve("-incomplete.md") + " is missing `## Verification`"),
                violations(modernFollowUps(tempDir)));
    }

    private static List<String> sequenceViolations(List<FollowUpFile> followUps) {
        List<String> violations = new ArrayList<>();
        int previous = MODERN_FOLLOW_UP_MIN_ID - 1;
        for (FollowUpFile followUp : followUps) {
            int current = Integer.parseInt(followUp.id());
            if (current == previous) {
                violations.add("Duplicate modern followUp id follow-up-" + followUp.id());
                continue;
            }
            for (int missing = previous + 1; missing < current; missing++) {
                violations.add("Missing modern followUp id follow-up-"
                        + String.format("%03d", missing)
                        + " between follow-up-"
                        + String.format("%03d", previous)
                        + " and follow-up-"
                        + followUp.id());
            }
            previous = current;
        }
        return violations;
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
            return Optional.of(new FollowUpFile(path, matcher.group(1), title, status, lines));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read followUp " + path, exception);
        }
    }

    private static void write(Path path, String contents) throws IOException {
        Files.writeString(path, contents);
    }

    private static void writeModernFollowUp(Path path, String id) throws IOException {
        write(path, """
                # follow-up-%s - FollowUp

                Status: Done

                - [x] Complete
                """.formatted(id));
    }

    private static String describe(List<String> values) {
        StringBuilder description = new StringBuilder();
        for (String value : values) {
            description.append("- ").append(value).append('\n');
        }
        return description.toString();
    }

    private record FollowUpFile(Path path, String id, String title, Optional<String> status, List<String> lines) {
        private boolean hasUncheckedChecklistItem() {
            return lines.stream().anyMatch(line -> line.trim().startsWith("- [ ]"));
        }

        private boolean hasSection(String name) {
            return lines.stream().anyMatch(line -> line.equals("## " + name));
        }
    }
}
