package sh.zolt.build.generatedsource;

import sh.zolt.build.BuildException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Resolves a process-tool {@code binary} to an absolute executable on the curated PATH, first match
 * wins. Zolt owns the lookup (rather than delegating to the child process's own PATH resolution) so
 * the discovered path is deterministic and recordable in plan/fingerprint evidence.
 */
final class ProcessToolLocator {
    private ProcessToolLocator() {
    }

    static Path locate(String binary, String pathValue, String pathSeparator, String subject) {
        Path direct = Path.of(binary);
        if (direct.getNameCount() != 1 || direct.isAbsolute()) {
            if (isExecutable(direct)) {
                return direct.toAbsolutePath().normalize();
            }
            throw notFound(binary, subject);
        }
        if (pathValue != null && !pathValue.isBlank()) {
            for (String entry : pathValue.split(Pattern.quote(pathSeparator), -1)) {
                if (entry.isBlank()) {
                    continue;
                }
                Path candidate = Path.of(entry).resolve(binary);
                if (isExecutable(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            }
        }
        throw notFound(binary, subject);
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
