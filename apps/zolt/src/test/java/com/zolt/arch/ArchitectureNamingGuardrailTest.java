package com.zolt.arch;

import static com.zolt.arch.ArchitectureDiagnostics.describe;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

final class ArchitectureNamingGuardrailTest {
    private static final Path ALLOWLIST = RepositoryPaths.appRoot()
            .resolve("src/test/resources/com/zolt/arch/production-catch-all-name-allowlist.txt");
    private static final Pattern CATCH_ALL_PRODUCTION_NAME =
            Pattern.compile(".*(?:Utils|Helper|Common|Support)\\.java");

    @Test
    void productionSourcesAvoidNewCatchAllOwnerNames() throws IOException {
        Set<String> catchAllFiles = catchAllProductionFiles(RepositoryPaths.mainSourceRoots());
        Map<String, AllowlistEntry> allowlist = readAllowlist(ALLOWLIST);
        List<String> violations = new ArrayList<>();

        for (String path : catchAllFiles) {
            if (!allowlist.containsKey(path)) {
                violations.add(path + " uses a catch-all production owner name; rename it or add a planned exception");
            }
        }
        allowlist.keySet().stream()
                .filter(path -> !catchAllFiles.contains(path))
                .sorted()
                .forEach(path -> violations.add(path + " no longer exists as a catch-all name; remove the allowlist entry"));

        assertTrue(
                violations.isEmpty(),
                () -> "Production catch-all naming violations:\n"
                        + describe(violations)
                        + "\nPrefer behavior-specific names from `docs/code-organization.md`.");
    }

    @Test
    void scannerFindsCatchAllProductionNames(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        write(sourceRoot.resolve("com/example/FocusedOwner.java"), "final class FocusedOwner {}\n");
        write(sourceRoot.resolve("com/example/BuildHelper.java"), "final class BuildHelper {}\n");
        write(sourceRoot.resolve("com/example/StringUtils.java"), "final class StringUtils {}\n");

        assertEquals(
                Set.of(
                        RepositoryPaths.displayPath(sourceRoot.resolve("com/example/BuildHelper.java")),
                        RepositoryPaths.displayPath(sourceRoot.resolve("com/example/StringUtils.java"))),
                catchAllProductionFiles(List.of(sourceRoot)));
    }

    @Test
    void allowlistParserReadsTrackedExceptions(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                # path|followUp|reason
                modules/example/src/main/java/com/example/FocusedSupport.java||shared package behavior
                """);

        assertEquals(
                Map.of(
                        "modules/example/src/main/java/com/example/FocusedSupport.java",
                        new AllowlistEntry(
                                "modules/example/src/main/java/com/example/FocusedSupport.java",
                                "",
                                "shared package behavior")),
                readAllowlist(allowlist));
    }

    private static Set<String> catchAllProductionFiles(List<Path> sourceRoots) throws IOException {
        Set<String> files = new TreeSet<>();
        for (Path sourceRoot : sourceRoots) {
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> CATCH_ALL_PRODUCTION_NAME.matcher(path.getFileName().toString()).matches())
                        .map(RepositoryPaths::displayPath)
                        .forEach(files::add);
            }
        }
        return files;
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
                throw new IllegalArgumentException("Duplicate production naming allowlist entry: "
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
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid production naming allowlist line: " + line);
        }
        return Optional.of(new AllowlistEntry(parts[0], parts[1], parts[2]));
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }

    private record AllowlistEntry(String path, String followUp, String reason) {
    }
}
