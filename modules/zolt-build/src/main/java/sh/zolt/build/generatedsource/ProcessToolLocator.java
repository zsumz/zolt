package sh.zolt.build.generatedsource;

import sh.zolt.build.BuildException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Resolves a process-tool {@code binary} to an absolute executable on the curated PATH, first match
 * wins. Zolt owns the lookup (rather than delegating to the child process's own PATH resolution) so
 * the discovered path is deterministic and recordable in plan/fingerprint evidence.
 */
final class ProcessToolLocator {
    // Windows resolves a bare command name against PATHEXT; mirror the default set so a
    // process-tool `binary` like `protoc` also matches `protoc.exe`/`protoc.cmd` on PATH.
    private static final List<String> WINDOWS_EXECUTABLE_SUFFIXES =
            List.of("", ".exe", ".bat", ".cmd", ".com");
    private static final List<String> POSIX_EXECUTABLE_SUFFIXES = List.of("");

    private ProcessToolLocator() {
    }

    static Path locate(String binary, String pathValue, String pathSeparator, String subject) {
        return locate(binary, pathValue, pathSeparator, subject, System.getProperty("os.name", ""));
    }

    static Path locate(String binary, String pathValue, String pathSeparator, String subject, String osName) {
        List<String> suffixes = executableSuffixes(osName, binary);
        Path direct = Path.of(binary);
        if (direct.getNameCount() != 1 || direct.isAbsolute()) {
            for (String suffix : suffixes) {
                Path candidate = Path.of(binary + suffix);
                if (isExecutable(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            }
            throw notFound(binary, subject);
        }
        if (pathValue != null && !pathValue.isBlank()) {
            for (String entry : pathValue.split(Pattern.quote(pathSeparator), -1)) {
                if (entry.isBlank()) {
                    continue;
                }
                Path directory = Path.of(entry);
                for (String suffix : suffixes) {
                    Path candidate = directory.resolve(binary + suffix);
                    if (isExecutable(candidate)) {
                        return candidate.toAbsolutePath().normalize();
                    }
                }
            }
        }
        throw notFound(binary, subject);
    }

    private static List<String> executableSuffixes(String osName, String binary) {
        if (!osName.toLowerCase(Locale.ROOT).contains("win")) {
            return POSIX_EXECUTABLE_SUFFIXES;
        }
        String lower = binary.toLowerCase(Locale.ROOT);
        for (String suffix : WINDOWS_EXECUTABLE_SUFFIXES) {
            if (!suffix.isEmpty() && lower.endsWith(suffix)) {
                // Already carries an executable extension; do not append another.
                return POSIX_EXECUTABLE_SUFFIXES;
            }
        }
        return WINDOWS_EXECUTABLE_SUFFIXES;
    }

    private static boolean isExecutable(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }

    private static BuildException notFound(String binary, String subject) {
        return BuildException.actionable(
                "Exec step " + subject + " could not find process binary `" + binary + "` on the curated PATH.",
                "Install `" + binary + "` and ensure it is on PATH, or fix the binary name in the tool declaration.");
    }
}
