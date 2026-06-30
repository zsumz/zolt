package com.zolt.arch;

import static com.zolt.arch.ArchitectureDiagnostics.describe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Architecture guardrail: production (`src/main`) Java sources must not dump raw stack traces or
 * write directly to {@code System.out}/{@code System.err}. Subprocess workers route failures through
 * {@code WorkerFailureDiagnostic.causeLine} so the parent launcher surfaces a single parseable line.
 *
 * <p>The single deliberately retained stdout report (the per-test failure trace in
 * {@code QuarkusAnnotationProgrammaticRunner}) is recorded in
 * {@code worker-output-allowlist.txt} with a followUp, owner, gate, and reason.
 *
 * <p>String/text-block literals and comments are stripped before scanning so the generated scaffold
 * {@code System.out.println} inside {@code ProjectInitializer} (emitted into a new user project, not
 * executed by Zolt) is never flagged.
 */
final class WorkerOutputGuardrailTest {
    private static final Path ALLOWLIST = RepositoryPaths.appRoot()
            .resolve("src/test/resources/com/zolt/arch/worker-output-allowlist.txt");
    private static final Pattern PRINT_STACK_TRACE = Pattern.compile("\\.\\s*printStackTrace\\s*\\(");
    private static final Pattern SYSTEM_STREAM_WRITE = Pattern.compile(
            "System\\s*\\.\\s*(?:out|err)\\s*\\.\\s*(?:print|println|printf|append|write|format)\\s*\\(");

    @Test
    void productionSourcesRouteWorkerFailuresThroughStructuredDiagnostics() throws IOException {
        Set<String> violations = workerOutputViolations(RepositoryPaths.mainSourceRoots());
        Map<String, AllowlistEntry> allowlist = readAllowlist(ALLOWLIST);
        List<String> failures = new ArrayList<>();

        for (String path : violations) {
            if (!allowlist.containsKey(path)) {
                failures.add(path
                        + " dumps a raw stack trace or writes to System.out/System.err; route worker"
                        + " failures through WorkerFailureDiagnostic.causeLine or add a planned allowlist entry");
            }
        }
        allowlist.keySet().stream()
                .filter(path -> !violations.contains(path))
                .sorted()
                .forEach(path -> failures.add(
                        path + " no longer writes raw worker output; remove the allowlist entry"));

        assertTrue(
                failures.isEmpty(),
                () -> "Worker output guardrail violations:\n"
                        + describe(failures)
                        + "\nSee `docs/worker-output-guardrails.md`.");
    }

    @Test
    void scannerFlagsPrintStackTraceAndSystemStreamWrites(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/com/example");
        write(sourceRoot.resolve("RawStackTrace.java"), """
                final class RawStackTrace {
                    void run(java.io.PrintStream err, Exception exception) {
                        exception.printStackTrace(err);
                    }
                }
                """);
        write(sourceRoot.resolve("RawSystemOut.java"), """
                final class RawSystemOut {
                    void run() {
                        System.out.println("hello");
                    }
                }
                """);
        write(sourceRoot.resolve("Structured.java"), """
                import com.zolt.error.WorkerFailureDiagnostic;
                final class Structured {
                    void run(java.io.PrintStream err, Exception exception) {
                        err.println(WorkerFailureDiagnostic.causeLine(exception));
                    }
                }
                """);

        assertEquals(
                Set.of(
                        RepositoryPaths.displayPath(sourceRoot.resolve("RawStackTrace.java")),
                        RepositoryPaths.displayPath(sourceRoot.resolve("RawSystemOut.java"))),
                workerOutputViolations(List.of(sourceRoot)));
    }

    @Test
    void scannerIgnoresSystemOutInStringLiteralsAndTextBlocks(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java/com/example");
        write(sourceRoot.resolve("Scaffold.java"), """
                final class Scaffold {
                    private static String mainSource() {
                        return \"""
                                public final class Main {
                                    public static void main(String[] args) {
                                        System.out.println("Hello!");
                                    }
                                }
                                \""";
                    }

                    private static String inline() {
                        return "System.out.println(\\"x\\");";
                    }

                    // System.out.println in a comment must also be ignored.
                    void run(java.io.PrintStream err, Exception exception) {
                        err.println(exception.getMessage());
                    }
                }
                """);

        assertEquals(Set.of(), workerOutputViolations(List.of(sourceRoot)));
    }

    @Test
    void allowlistParserRequiresOwnerReasonAndGate(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                # path|followUp|owner|gate|reason
                modules/example/src/main/java/com/example/Worker.java||quarkus worker|PR|per-test stdout report
                """);

        assertEquals(
                Map.of(
                        "modules/example/src/main/java/com/example/Worker.java",
                        new AllowlistEntry(
                                "modules/example/src/main/java/com/example/Worker.java",
                                "",
                                "quarkus worker",
                                "PR",
                                "per-test stdout report")),
                readAllowlist(allowlist));
    }

    @Test
    void allowlistParserRejectsMalformedLines(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, "modules/example/src/main/java/com/example/Worker.java||quarkus worker|PR\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readAllowlist(allowlist));

        assertEquals(
                "Invalid worker-output allowlist line: "
                        + "modules/example/src/main/java/com/example/Worker.java||quarkus worker|PR",
                exception.getMessage());
    }

    @Test
    void allowlistParserRejectsDuplicatePaths(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                modules/example/src/main/java/com/example/Worker.java||quarkus worker|PR|first
                modules/example/src/main/java/com/example/Worker.java||quarkus worker|PR|second
                """);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readAllowlist(allowlist));

        assertEquals(
                "Duplicate worker-output allowlist entry: modules/example/src/main/java/com/example/Worker.java",
                exception.getMessage());
    }

    @Test
    void docsIndexLinksWorkerOutputGuardrails() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));

        assertTrue(docsIndex.contains("`worker-output-guardrails.md`"));
    }

    private static Set<String> workerOutputViolations(List<Path> sourceRoots) throws IOException {
        Set<String> files = new TreeSet<>();
        for (Path javaFile : ArchitectureSourceFiles.javaFiles(sourceRoots)) {
            String scannable = stripLiteralsAndComments(Files.readString(javaFile));
            if (PRINT_STACK_TRACE.matcher(scannable).find() || SYSTEM_STREAM_WRITE.matcher(scannable).find()) {
                files.add(RepositoryPaths.displayPath(javaFile));
            }
        }
        return files;
    }

    /**
     * Replaces the body of every comment, string literal, and text block with neutral filler so the
     * scanner only sees real statement positions. Filler preserves line breaks but cannot contain
     * {@code printStackTrace(} or {@code System.out.print(}, so literals like the generated scaffold
     * {@code System.out.println} are never matched.
     */
    static String stripLiteralsAndComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        int index = 0;
        int length = source.length();
        while (index < length) {
            char current = source.charAt(index);
            if (current == '/' && index + 1 < length && source.charAt(index + 1) == '/') {
                index = skipLineComment(source, index, result);
            } else if (current == '/' && index + 1 < length && source.charAt(index + 1) == '*') {
                index = skipBlockComment(source, index, result);
            } else if (current == '"' && source.startsWith("\"\"\"", index)) {
                index = skipTextBlock(source, index, result);
            } else if (current == '"') {
                index = skipStringLiteral(source, index, result);
            } else if (current == '\'') {
                index = skipCharLiteral(source, index, result);
            } else {
                result.append(current);
                index++;
            }
        }
        return result.toString();
    }

    private static int skipLineComment(String source, int start, StringBuilder result) {
        int index = start + 2;
        while (index < source.length() && source.charAt(index) != '\n') {
            index++;
        }
        return index;
    }

    private static int skipBlockComment(String source, int start, StringBuilder result) {
        int index = start + 2;
        while (index < source.length() && !source.startsWith("*/", index)) {
            if (source.charAt(index) == '\n') {
                result.append('\n');
            }
            index++;
        }
        return Math.min(index + 2, source.length());
    }

    private static int skipTextBlock(String source, int start, StringBuilder result) {
        int index = start + 3;
        while (index < source.length() && !source.startsWith("\"\"\"", index)) {
            if (source.charAt(index) == '\\') {
                index += 2;
                continue;
            }
            if (source.charAt(index) == '\n') {
                result.append('\n');
            }
            index++;
        }
        return Math.min(index + 3, source.length());
    }

    private static int skipStringLiteral(String source, int start, StringBuilder result) {
        int index = start + 1;
        while (index < source.length() && source.charAt(index) != '"') {
            if (source.charAt(index) == '\\') {
                index += 2;
                continue;
            }
            if (source.charAt(index) == '\n') {
                break;
            }
            index++;
        }
        return Math.min(index + 1, source.length());
    }

    private static int skipCharLiteral(String source, int start, StringBuilder result) {
        int index = start + 1;
        while (index < source.length() && source.charAt(index) != '\'') {
            if (source.charAt(index) == '\\') {
                index += 2;
                continue;
            }
            if (source.charAt(index) == '\n') {
                break;
            }
            index++;
        }
        return Math.min(index + 1, source.length());
    }

    private static Map<String, AllowlistEntry> readAllowlist(Path path) throws IOException {
        return ArchitectureAllowlistSupport.readAllowlist(
                path,
                WorkerOutputGuardrailTest::parseAllowlistLine,
                AllowlistEntry::path,
                "Duplicate worker-output allowlist entry: ");
    }

    private static Optional<AllowlistEntry> parseAllowlistLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Optional.empty();
        }
        String[] parts = trimmed.split("\\|", -1);
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid worker-output allowlist line: " + line);
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
