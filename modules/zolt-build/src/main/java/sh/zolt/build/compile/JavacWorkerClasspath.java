package sh.zolt.build.compile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class JavacWorkerClasspath {
    static final String PROPERTY = "zolt.javac.worker.classpath";
    static final String ENVIRONMENT = "ZOLT_JAVAC_WORKER_CLASSPATH";
    static final String BUNDLED_WORKER_JAR = "zolt-javac-worker.jar";

    private JavacWorkerClasspath() {
    }

    static Optional<Path> discover() {
        Optional<Path> configured = configuredPath(System.getProperty(PROPERTY));
        if (configured.isPresent()) {
            return configured;
        }
        configured = configuredPath(System.getenv(ENVIRONMENT));
        if (configured.isPresent()) {
            return configured;
        }
        return bundledCandidates().stream().filter(Files::isRegularFile).findFirst();
    }

    private static Optional<Path> configuredPath(String configured) {
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    private static List<Path> bundledCandidates() {
        Optional<Path> executable = ProcessHandle.current().info().command().map(Path::of);
        if (executable.isEmpty()) {
            return List.of();
        }
        Path command = executable.orElseThrow().toAbsolutePath().normalize();
        Path parent = command.getParent();
        if (parent == null) {
            return List.of();
        }
        List<Path> candidates = new ArrayList<>();
        candidates.add(parent.resolve("libexec").resolve(BUNDLED_WORKER_JAR));
        Path installRoot = parent.getParent();
        if (installRoot != null && "bin".equals(parent.getFileName().toString())) {
            candidates.add(installRoot.resolve("libexec").resolve(BUNDLED_WORKER_JAR));
        }
        return candidates.stream().map(path -> path.toAbsolutePath().normalize()).distinct().toList();
    }
}
