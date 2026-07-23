package sh.zolt.build.generatedsource;

import sh.zolt.build.BuildException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Catches files a cacheable step writes outside its declared output. The scan is bounded to the step's
 * cwd subtree: it snapshots regular-file mtimes before the run (excluding the declared output and the
 * exec sidecar metadata directory), then after the run flags any file that is new or whose mtime
 * advanced. Skipped for {@code cache = "none"} steps, whose nondeterminism is already declared.
 */
final class ExecUndeclaredOutputScan {
    private ExecUndeclaredOutputScan() {
    }

    static Map<Path, Long> snapshot(Path cwd, Path output, Path metadataDirectory) {
        Map<Path, Long> mtimes = new LinkedHashMap<>();
        walk(cwd, output, metadataDirectory, (path, modified) -> mtimes.put(path, modified));
        return mtimes;
    }

    static void verify(
            String subject,
            Path cwd,
            Path output,
            Path metadataDirectory,
            Map<Path, Long> before) {
        List<Path> undeclared = new ArrayList<>();
        walk(cwd, output, metadataDirectory, (path, modified) -> {
            Long previous = before.get(path);
            if (previous == null || modified > previous) {
                undeclared.add(path);
            }
        });
        if (undeclared.isEmpty()) {
            return;
        }
        undeclared.sort(null);
        String paths = undeclared.stream()
                .map(path -> relativize(cwd, path))
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        throw BuildException.actionable(
                "Exec step " + subject + " wrote files outside its declared output: " + paths + ".",
                "Declare each path as an output (or route it under the output directory), or move it out of the cwd; "
                        + "content-cached steps may only write into their declared output.");
    }

    private static void walk(Path cwd, Path output, Path metadataDirectory, MtimeVisitor visitor) {
        if (!Files.isDirectory(cwd)) {
            return;
        }
        Path normalizedOutput = output.toAbsolutePath().normalize();
        Path normalizedMetadata = metadataDirectory.toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(cwd)) {
            paths.map(path -> path.toAbsolutePath().normalize())
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(normalizedOutput))
                    .filter(path -> !path.startsWith(normalizedMetadata))
                    .forEach(path -> visitor.visit(path, lastModified(path)));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not scan exec cwd " + cwd + " for undeclared outputs. Check that it is readable.",
                    exception);
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static String relativize(Path cwd, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path base = cwd.toAbsolutePath().normalize();
        return (normalized.startsWith(base) ? base.relativize(normalized) : normalized).toString().replace('\\', '/');
    }

    @FunctionalInterface
    private interface MtimeVisitor {
        void visit(Path path, long modified);
    }
}
