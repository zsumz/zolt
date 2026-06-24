package com.zolt.arch;

import static com.zolt.arch.ArchitectureDiagnostics.describe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FixedSleepGuardrailTest {
    private static final Path ALLOWLIST = RepositoryPaths.appRoot()
            .resolve("src/test/resources/com/zolt/arch/fixed-sleep-test-allowlist.txt");
    private static final Pattern FIXED_SLEEP = Pattern.compile(
            ".*(?:Thread\\.sleep\\s*\\(|TimeUnit\\.[A-Z_]+\\.sleep\\s*\\().*");

    @Test
    void javaTestSourcesAvoidNewFixedSleeps() throws IOException {
        Set<String> fixedSleepFiles = fixedSleepFiles(RepositoryPaths.testSourceRoots());
        Map<String, AllowlistEntry> allowlist = readAllowlist(ALLOWLIST);
        List<String> violations = new ArrayList<>();

        for (String path : fixedSleepFiles) {
            if (!allowlist.containsKey(path)) {
                violations.add(path + " uses a fixed sleep in test code; use deterministic synchronization or add a planned exception");
            }
        }
        allowlist.keySet().stream()
                .filter(path -> !fixedSleepFiles.contains(path))
                .sorted()
                .forEach(path -> violations.add(path + " no longer contains a fixed sleep; remove the allowlist entry"));

        assertTrue(
                violations.isEmpty(),
                () -> "Fixed-sleep test guardrail violations:\n"
                        + describe(violations)
                        + "\nSee `docs/no-sleep-test-guardrails.md`.");
    }

    @Test
    void scannerFindsFixedSleepsInJavaTests(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/test/java");
        write(sourceRoot.resolve("com/example/NoSleepTest.java"), "final class NoSleepTest {}\n");
        write(sourceRoot.resolve("com/example/ThreadSleepTest.java"), ("""
                final class ThreadSleepTest {
                    void waits() throws Exception {
                        Thread.%s(10L);
                    }
                }
                """).formatted("sleep"));
        write(sourceRoot.resolve("com/example/TimeUnitSleepTest.java"), ("""
                import java.util.concurrent.TimeUnit;
                final class TimeUnitSleepTest {
                    void waits() throws Exception {
                        TimeUnit.MILLISECONDS.%s(10L);
                    }
                }
                """).formatted("sleep"));

        assertEquals(
                Set.of(
                        RepositoryPaths.displayPath(sourceRoot.resolve("com/example/ThreadSleepTest.java")),
                        RepositoryPaths.displayPath(sourceRoot.resolve("com/example/TimeUnitSleepTest.java"))),
                fixedSleepFiles(List.of(sourceRoot)));
    }

    @Test
    void allowlistParserRequiresOwnerReasonAndGate(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                # path|followUp|owner|gate|reason
                modules/example/src/test/java/com/example/SlowTest.java||repository fake server|PR|configured fake delay
                """);

        assertEquals(
                Map.of(
                        "modules/example/src/test/java/com/example/SlowTest.java",
                        new AllowlistEntry(
                                "modules/example/src/test/java/com/example/SlowTest.java",
                                "",
                                "repository fake server",
                                "PR",
                                "configured fake delay")),
                readAllowlist(allowlist));
    }

    @Test
    void allowlistParserRejectsMalformedLines(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, "modules/example/src/test/java/com/example/SlowTest.java||repository fake server|PR\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readAllowlist(allowlist));

        assertEquals(
                "Invalid fixed-sleep allowlist line: modules/example/src/test/java/com/example/SlowTest.java||repository fake server|PR",
                exception.getMessage());
    }

    @Test
    void allowlistParserRejectsDuplicatePaths(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                modules/example/src/test/java/com/example/SlowTest.java||repository fake server|PR|configured fake delay
                modules/example/src/test/java/com/example/SlowTest.java||repository fake server|PR|still delayed
                """);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readAllowlist(allowlist));

        assertEquals(
                "Duplicate fixed-sleep allowlist entry: modules/example/src/test/java/com/example/SlowTest.java",
                exception.getMessage());
    }

    @Test
    void docsIndexLinksNoSleepGuardrails() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));

        assertTrue(docsIndex.contains("`no-sleep-test-guardrails.md`"));
    }

    private static Set<String> fixedSleepFiles(List<Path> sourceRoots) throws IOException {
        Set<String> files = new TreeSet<>();
        for (Path sourceRoot : sourceRoots) {
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                for (Path javaFile : paths.filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .toList()) {
                    if (containsFixedSleep(javaFile)) {
                        files.add(RepositoryPaths.displayPath(javaFile));
                    }
                }
            }
        }
        return files;
    }

    private static boolean containsFixedSleep(Path javaFile) throws IOException {
        for (String line : Files.readAllLines(javaFile)) {
            if (FIXED_SLEEP.matcher(line).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, AllowlistEntry> readAllowlist(Path path) throws IOException {
        Map<String, AllowlistEntry> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            Optional<AllowlistEntry> entry = parseAllowlistLine(line);
            if (entry.isEmpty()) {
                continue;
            }
            AllowlistEntry allowlistEntry = entry.orElseThrow();
            AllowlistEntry previous = entries.put(allowlistEntry.path(), allowlistEntry);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate fixed-sleep allowlist entry: "
                        + allowlistEntry.path());
            }
        }
        return entries;
    }

    private static Optional<AllowlistEntry> parseAllowlistLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Optional.empty();
        }
        String[] parts = trimmed.split("\\|", -1);
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid fixed-sleep allowlist line: " + line);
        }
        return Optional.of(new AllowlistEntry(parts[0], parts[1], parts[2], parts[3], parts[4]));
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }

    private record AllowlistEntry(String path, String followUp, String owner, String gate, String reason) {
    }
}
