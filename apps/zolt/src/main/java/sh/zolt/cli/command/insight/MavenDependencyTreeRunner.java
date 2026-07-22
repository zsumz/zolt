package sh.zolt.cli.command.insight;

import sh.zolt.error.ActionableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the project's Maven to extract resolved dependencies per reactor module, via a single
 * {@code dependency:tree} invocation writing to a file.
 *
 * <p>Command: {@code <mvn> -q -B dependency:tree -DoutputType=text -DoutputFile=<tmp>}
 * {@code -DappendOutput=true}. Maven is preferred as {@code ./mvnw} (the project's pinned wrapper)
 * when present, else {@code mvn} on {@code PATH}. Writing the tree to a file (not the log) keeps the
 * output clean under {@code -q} and lets every reactor module append its own resolved tree, whose
 * zero-indent root line carries the module's {@code group:artifact} identity.
 */
final class MavenDependencyTreeRunner {

    String run(Path projectRoot) {
        if (!Files.isRegularFile(projectRoot.resolve("pom.xml"))) {
            throw new ActionableException(
                    "No pom.xml found at " + projectRoot + "; `zolt explain verify` reads a Maven project.",
                    "Run it from a Maven project root, or pass --directory <path> to point at one.");
        }
        List<String> command = mavenCommand(projectRoot);
        Path outputFile;
        try {
            outputFile = Files.createTempFile("zolt-verify-tree", ".txt");
        } catch (IOException exception) {
            throw new ActionableException(
                    "Could not create a temporary file for Maven output: " + exception.getMessage() + ".",
                    "Ensure the system temp directory is writable and retry.");
        }
        try {
            return execute(command, projectRoot, outputFile);
        } finally {
            deleteQuietly(outputFile);
        }
    }

    private String execute(List<String> command, Path projectRoot, Path outputFile) {
        List<String> fullCommand = new ArrayList<>(command);
        fullCommand.add("-q");
        fullCommand.add("-B");
        fullCommand.add("dependency:tree");
        fullCommand.add("-DoutputType=text");
        fullCommand.add("-DoutputFile=" + outputFile.toAbsolutePath());
        fullCommand.add("-DappendOutput=true");

        ProcessBuilder builder = new ProcessBuilder(fullCommand)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true);
        String captured;
        int exitCode;
        Process process;
        try {
            process = builder.start();
        } catch (IOException exception) {
            throw new ActionableException(
                    "Could not launch Maven (" + command.get(0) + ") in " + projectRoot + ": "
                            + exception.getMessage() + ".",
                    "Install Maven and put `mvn` on PATH, or add the Maven wrapper (./mvnw) to the project.");
        }
        try {
            captured = readAll(process.getInputStream());
            exitCode = process.waitFor();
        } catch (IOException exception) {
            throw new ActionableException(
                    "Failed while reading Maven output: " + exception.getMessage() + ".",
                    "Re-run `" + command.get(0) + " dependency:tree` in " + projectRoot + " to reproduce.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ActionableException(
                    "Interrupted while waiting for Maven to finish.",
                    "Re-run `zolt explain verify` once the machine is idle.");
        }
        if (exitCode != 0) {
            throw new ActionableException(
                    "Maven `dependency:tree` failed with exit code " + exitCode + " in " + projectRoot
                            + "." + lastLineHint(captured),
                    "Run `" + command.get(0) + " -e dependency:tree` in that directory to see the full error.");
        }
        return readOutputFile(outputFile, command.get(0), projectRoot);
    }

    private static String readOutputFile(Path outputFile, String mavenBinary, Path projectRoot) {
        try {
            if (!Files.isRegularFile(outputFile)) {
                throw new ActionableException(
                        "Maven completed but wrote no dependency:tree output.",
                        "Run `" + mavenBinary + " dependency:tree` in " + projectRoot
                                + " and confirm the dependency plugin is available.");
            }
            return Files.readString(outputFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ActionableException(
                    "Could not read Maven dependency:tree output: " + exception.getMessage() + ".",
                    "Re-run `zolt explain verify` after confirming the temp directory is readable.");
        }
    }

    private static List<String> mavenCommand(Path projectRoot) {
        // Prefer the project's pinned wrapper, but only when it is actually runnable; a present but
        // non-executable mvnw falls through to `mvn` on PATH rather than failing with error=13.
        Path wrapper = projectRoot.resolve("mvnw");
        if (Files.isExecutable(wrapper)) {
            return new ArrayList<>(List.of(wrapper.toAbsolutePath().toString()));
        }
        Path wrapperWindows = projectRoot.resolve("mvnw.cmd");
        if (Files.isRegularFile(wrapperWindows)) {
            return new ArrayList<>(List.of(wrapperWindows.toAbsolutePath().toString()));
        }
        return new ArrayList<>(List.of("mvn"));
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
        return " Last Maven output: " + lastLine;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup of a temp file; nothing actionable for the user here.
        }
    }
}
