package sh.zolt.release.archive;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ReleaseArchiveLayout {
    private static final String JUNIT_WORKER_ARTIFACT = "zolt-junit-worker";
    private static final String JUNIT_WORKER_ARCHIVE_NAME = "zolt-junit-worker.jar";

    List<ReleaseArchiveEntry> entries(
            Path projectDirectory,
            Path binary,
            String rootDirectory,
            String binaryName,
            String version,
            Optional<String> releaseProvenanceJson) {
        List<ReleaseArchiveEntry> entries = new ArrayList<>();
        entries.add(ReleaseArchiveEntry.directory(rootDirectory + "/"));
        entries.add(ReleaseArchiveEntry.directory(rootDirectory + "/bin/"));
        entries.add(ReleaseArchiveEntry.file(binary, rootDirectory + "/bin/" + binaryName, 0755));
        addJunitWorkerIfPresent(entries, projectDirectory, rootDirectory, version);
        entries.add(ReleaseArchiveEntry.content(
                (version + "\n").getBytes(StandardCharsets.UTF_8),
                rootDirectory + "/VERSION",
                0644));
        releaseProvenanceJson.ifPresent(json -> entries.add(ReleaseArchiveEntry.content(
                json.getBytes(StandardCharsets.UTF_8),
                rootDirectory + "/BUILD.json",
                0644)));
        addIfPresent(entries, projectDirectory.resolve("README.md"), rootDirectory + "/README.md", 0644);
        addIfPresent(entries, projectDirectory.resolve("LICENSE"), rootDirectory + "/LICENSE", 0644);
        return entries;
    }

    private static void addJunitWorkerIfPresent(
            List<ReleaseArchiveEntry> entries,
            Path projectDirectory,
            String rootDirectory,
            String version) {
        Path workerJar = junitWorkerJar(projectDirectory, version);
        if (!Files.isRegularFile(workerJar)) {
            return;
        }
        entries.add(ReleaseArchiveEntry.directory(rootDirectory + "/libexec/"));
        entries.add(ReleaseArchiveEntry.file(workerJar, rootDirectory + "/libexec/" + JUNIT_WORKER_ARCHIVE_NAME, 0644));
    }

    private static Path junitWorkerJar(Path projectDirectory, String version) {
        String fileName = JUNIT_WORKER_ARTIFACT + "-" + version + ".jar";
        List<Path> candidates = new ArrayList<>();
        candidates.add(projectDirectory.resolve("target/libexec").resolve(JUNIT_WORKER_ARCHIVE_NAME));
        candidates.add(projectDirectory.resolve("target").resolve(fileName));
        candidates.add(projectDirectory.resolve("apps/" + JUNIT_WORKER_ARTIFACT + "/target").resolve(fileName));
        siblingWorkerJar(projectDirectory, fileName).forEach(candidates::add);
        return candidates.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(candidates.getFirst().toAbsolutePath().normalize());
    }

    private static List<Path> siblingWorkerJar(Path projectDirectory, String fileName) {
        Path parent = projectDirectory.getParent();
        if (parent == null || projectDirectory.getFileName() == null || parent.getFileName() == null) {
            return List.of();
        }
        if (!"zolt".equals(projectDirectory.getFileName().toString())
                || !"apps".equals(parent.getFileName().toString())) {
            return List.of();
        }
        return List.of(parent.resolve(JUNIT_WORKER_ARTIFACT).resolve("target").resolve(fileName));
    }

    private static void addIfPresent(List<ReleaseArchiveEntry> entries, Path source, String name, int mode) {
        if (Files.isRegularFile(source)) {
            entries.add(ReleaseArchiveEntry.file(source, name, mode));
        }
    }
}
