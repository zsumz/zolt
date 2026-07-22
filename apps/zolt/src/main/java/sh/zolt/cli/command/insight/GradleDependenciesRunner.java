package sh.zolt.cli.command.insight;

import sh.zolt.error.ActionableException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs the project's Gradle to extract resolved dependencies for every discovered project in a single
 * invocation.
 *
 * <p>Command: {@code <gradle> -q --console=plain [--offline] :dependencies :app:dependencies ...}. One
 * invocation requests the {@code dependencies} task of each project, so the whole build's resolved
 * graph comes back in one Gradle start-up, each project delimited by its {@code Root project '<name>'} /
 * {@code Project ':<path>'} title. Gradle is preferred as {@code ./gradlew} (the project's pinned
 * wrapper) when it is present and executable, else {@code gradle} on {@code PATH}; when neither exists
 * the command fails with install guidance rather than a raw launch error.
 *
 * <p>Failure modes: a non-zero Gradle exit (an unresolvable dependency, a missing included build, a
 * dynamic version that cannot be resolved, offline with an empty cache) is surfaced as an actionable
 * error carrying Gradle's last output line and a reproduce hint — the verifier never fabricates a
 * comparison from a failed resolution.
 */
final class GradleDependenciesRunner {

    String run(Path projectRoot, List<String> projectPaths, boolean offline) {
        if (projectPaths == null || projectPaths.isEmpty()) {
            throw new ActionableException(
                    "No Gradle projects with a build script were found under " + projectRoot + ".",
                    "Confirm the build has a build.gradle[.kts] (root or included projects) and retry.");
        }
        String gradle = gradleCommand(projectRoot).orElseThrow(() -> new ActionableException(
                "Neither a Gradle wrapper (./gradlew) nor `gradle` on PATH was found for " + projectRoot + ".",
                "Add the Gradle wrapper to the project, or install Gradle and put `gradle` on PATH, "
                        + "then re-run `zolt explain verify`."));

        List<String> command = new ArrayList<>();
        command.add(gradle);
        command.add("-q");
        command.add("--console=plain");
        if (offline) {
            command.add("--offline");
        }
        for (String projectPath : projectPaths) {
            command.add(task(projectPath));
        }
        return execute(command, projectRoot, gradle);
    }

    private static String execute(List<String> command, Path projectRoot, String gradle) {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException exception) {
            throw new ActionableException(
                    "Could not launch Gradle (" + gradle + ") in " + projectRoot + ": "
                            + exception.getMessage() + ".",
                    "Add the Gradle wrapper (./gradlew) to the project, or install Gradle and put "
                            + "`gradle` on PATH.");
        }
        String captured;
        int exitCode;
        try {
            captured = readAll(process.getInputStream());
            exitCode = process.waitFor();
        } catch (IOException exception) {
            throw new ActionableException(
                    "Failed while reading Gradle output: " + exception.getMessage() + ".",
                    "Re-run `" + gradle + " :dependencies` in " + projectRoot + " to reproduce.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ActionableException(
                    "Interrupted while waiting for Gradle to finish.",
                    "Re-run `zolt explain verify` once the machine is idle.");
        }
        if (exitCode != 0) {
            throw new ActionableException(
                    "Gradle `dependencies` failed with exit code " + exitCode + " in " + projectRoot
                            + "." + lastLineHint(captured),
                    "Run `" + gradle + " :dependencies` in that directory to see the full error; a build "
                            + "that does not resolve (dynamic versions, missing included builds) cannot be verified.");
        }
        return captured;
    }

    private static String task(String projectPath) {
        return ":".equals(projectPath) ? ":dependencies" : projectPath + ":dependencies";
    }

    private static Optional<String> gradleCommand(Path projectRoot) {
        Path wrapper = projectRoot.resolve("gradlew");
        if (Files.isExecutable(wrapper)) {
            return Optional.of(wrapper.toAbsolutePath().toString());
        }
        Path wrapperWindows = projectRoot.resolve("gradlew.bat");
        if (Files.isRegularFile(wrapperWindows)) {
            return Optional.of(wrapperWindows.toAbsolutePath().toString());
        }
        return onPath("gradle").map(Path::toString);
    }

    private static Optional<Path> onPath(String binary) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        for (String entry : path.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            Path candidate = Path.of(entry).resolve(binary);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate.toAbsolutePath());
            }
        }
        return Optional.empty();
    }

    private static String readAll(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String lastLineHint(String captured) {
        if (captured == null || captured.isBlank()) {
            return "";
        }
        String lastLine = "";
        for (String line : captured.split("\n")) {
            if (!line.isBlank()) {
                lastLine = line.strip();
            }
        }
        if (lastLine.isEmpty()) {
            return "";
        }
        if (lastLine.length() > 200) {
            lastLine = lastLine.substring(0, 200) + "…";
        }
        return " Last Gradle output: " + lastLine;
    }
}
