package sh.zolt.selfhost;

import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class NativeSmokeService {
    private final ProcessRunner processRunner;

    public NativeSmokeService() {
        this(NativeSmokeService::runProcess);
    }

    NativeSmokeService(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    public NativeSmokeResult smoke(
            Path projectDirectory,
            ProjectConfig config,
            Path binary,
            Path workDirectory) {
        Path root = projectDirectory.toAbsolutePath().normalize();
        Path resolvedBinary = root.resolve(binary).normalize();
        if (!Files.isRegularFile(resolvedBinary)) {
            throw new NativeSmokeException(
                    "Native smoke requires binary at "
                            + resolvedBinary
                            + ". Run `zolt native` or pass --binary <path>.");
        }

        Path workRoot = nativeSmokeWorkDirectory(root, workDirectory);
        resetWorkDirectory(workRoot);
        String expectedVersion = config.project().version();

        ProcessResult version = run("version", workRoot, resolvedBinary, List.of("--version"));
        if (!version.output().trim().equals(expectedVersion)) {
            throw new NativeSmokeException(
                    "Native smoke failed: expected `"
                            + resolvedBinary
                            + " --version` to print only `"
                            + expectedVersion
                            + "`. Output was:\n"
                            + version.output());
        }
        run("help", workRoot, resolvedBinary, List.of("help"));

        Path releaseDirectory = workRoot.resolve("release");
        String releaseBinary = projectRelativePath(root, "--binary", resolvedBinary);
        String releaseOutput = projectRelativePath(root, "--output", releaseDirectory);
        run("release archive", workRoot, resolvedBinary, List.of(
                "release-archive",
                "--cwd",
                root.toString(),
                "--binary",
                releaseBinary,
                "--output",
                releaseOutput));
        Path archive = singleReleaseArchive(releaseDirectory, config);
        run("release verify", workRoot, resolvedBinary, List.of(
                "release-verify",
                "--cwd",
                root.toString(),
                "--work-dir",
                workRoot.resolve("release-verify").toString(),
                archive.toString()));

        run("init", workRoot, resolvedBinary, List.of(
                "init",
                "--cwd",
                workRoot.toString(),
                "hello-native"));
        Path generatedProject = workRoot.resolve("hello-native");
        Path configFile = generatedProject.resolve("zolt.toml");
        if (!Files.isRegularFile(configFile)) {
            throw new NativeSmokeException("Native smoke failed: expected " + configFile + " to exist after init.");
        }

        String alias = "native-smoke";
        String aliasVersion = "0.0.1";
        run("version alias set", workRoot, resolvedBinary, List.of(
                "version",
                "set",
                "--cwd",
                generatedProject.toString(),
                "--no-resolve",
                alias,
                aliasVersion));
        requireConfigContains(configFile, alias, "after version alias set");
        run("version alias remove", workRoot, resolvedBinary, List.of(
                "version",
                "remove",
                "--cwd",
                generatedProject.toString(),
                "--no-resolve",
                alias));
        requireConfigDoesNotContain(configFile, alias, "after version alias remove");

        Path cacheRoot = workRoot.resolve("cache");
        run("resolve", workRoot, resolvedBinary, List.of(
                "resolve",
                "--cwd",
                generatedProject.toString(),
                "--cache-root",
                cacheRoot.toString()));
        run("build", workRoot, resolvedBinary, List.of(
                "build",
                "--cwd",
                generatedProject.toString(),
                "--cache-root",
                cacheRoot.toString()));
        ProcessResult run = run("run", workRoot, resolvedBinary, List.of(
                "run",
                "--cwd",
                generatedProject.toString(),
                "--cache-root",
                cacheRoot.toString()));
        if (!run.output().contains("Hello from hello-native!")) {
            throw new NativeSmokeException(
                    "Native smoke failed: expected generated project run output to contain `Hello from hello-native!`. Output was:\n"
                            + run.output());
        }
        run("package", workRoot, resolvedBinary, List.of(
                "package",
                "--cwd",
                generatedProject.toString(),
                "--cache-root",
                cacheRoot.toString()));
        Path generatedJar = generatedProject.resolve("target/hello-native-0.1.0.jar");
        if (!Files.isRegularFile(generatedJar)) {
            throw new NativeSmokeException(
                    "Native smoke failed: expected generated project package to write "
                            + generatedJar
                            + ".");
        }
        ProcessResult runPackage = run("run-package", workRoot, resolvedBinary, List.of(
                "run-package",
                "--cwd",
                generatedProject.toString(),
                "--cache-root",
                cacheRoot.toString()));
        if (!runPackage.output().contains("Hello from hello-native!")) {
            throw new NativeSmokeException(
                    "Native smoke failed: expected generated project run-package output to contain `Hello from hello-native!`. Output was:\n"
                            + runPackage.output());
        }

        return new NativeSmokeResult(resolvedBinary, workRoot, archive, generatedProject);
    }

    private static void requireConfigContains(Path configFile, String text, String step) {
        String content = readConfig(configFile, step);
        if (!content.contains(text)) {
            throw new NativeSmokeException(
                    "Native smoke failed: expected "
                            + configFile
                            + " to contain `"
                            + text
                            + "` "
                            + step
                            + ".");
        }
    }

    private static void requireConfigDoesNotContain(Path configFile, String text, String step) {
        String content = readConfig(configFile, step);
        if (content.contains(text)) {
            throw new NativeSmokeException(
                    "Native smoke failed: expected "
                            + configFile
                            + " not to contain `"
                            + text
                            + "` "
                            + step
                            + ".");
        }
    }

    private static String readConfig(Path configFile, String step) {
        try {
            return Files.readString(configFile);
        } catch (IOException exception) {
            throw new NativeSmokeException(
                    "Could not inspect generated native smoke project config "
                            + configFile
                            + " "
                            + step
                            + ". Check filesystem permissions.",
                    exception);
        }
    }

    private ProcessResult run(String step, Path directory, Path binary, List<String> arguments) {
        ProcessResult result = processRunner.run(command(binary, arguments), directory);
        if (result.exitCode() != 0) {
            throw new NativeSmokeException(
                    "Native smoke failed during "
                            + step
                            + " with exit code "
                            + result.exitCode()
                            + ". Output was:\n"
                            + result.output());
        }
        return result;
    }

    private static List<String> command(Path binary, List<String> arguments) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add(binary.toString());
        command.addAll(arguments);
        return List.copyOf(command);
    }

    private static String projectRelativePath(Path projectRoot, String key, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(projectRoot)) {
            throw new NativeSmokeException(
                    "Native smoke " + key + " path " + normalized + " must be under project directory " + projectRoot + ".");
        }
        return projectRoot.relativize(normalized).toString();
    }

    private static Path nativeSmokeWorkDirectory(Path projectRoot, Path workDirectory) {
        Path resolved = projectRoot.resolve(workDirectory).normalize();
        if (!resolved.startsWith(projectRoot) || resolved.equals(projectRoot)) {
            throw new NativeSmokeException(
                    "Native smoke --work-dir path "
                            + resolved
                            + " must be under project directory "
                            + projectRoot
                            + " and must not be the project directory itself.");
        }
        return resolved;
    }

    private static Path singleReleaseArchive(Path releaseDirectory, ProjectConfig config) {
        String prefix = config.project().name() + "-" + config.project().version() + "-";
        List<Path> archives = listFiles(releaseDirectory, path -> {
            String name = path.getFileName().toString();
            return name.startsWith(prefix) && (name.endsWith(".tar.gz") || name.endsWith(".zip"));
        });
        if (archives.size() != 1) {
            throw new NativeSmokeException(
                    "Native smoke failed: expected one release archive under "
                            + releaseDirectory
                            + " but found "
                            + archives.size()
                            + ".");
        }
        return archives.getFirst();
    }

    private static List<Path> listFiles(Path directory, Predicate<Path> filter) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(filter)
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new NativeSmokeException(
                    "Could not inspect native smoke directory " + directory + ". Check filesystem permissions.",
                    exception);
        }
    }

    private static void resetWorkDirectory(Path workDirectory) {
        try {
            if (Files.exists(workDirectory)) {
                try (var stream = Files.walk(workDirectory)) {
                    List<Path> paths = stream
                            .sorted(Comparator.reverseOrder())
                            .toList();
                    for (Path path : paths) {
                        Files.delete(path);
                    }
                }
            }
            Files.createDirectories(workDirectory);
        } catch (IOException exception) {
            throw new NativeSmokeException(
                    "Could not reset native smoke work directory "
                            + workDirectory
                            + ". Check that the directory is writable.",
                    exception);
        }
    }

    private static ProcessResult runProcess(List<String> command, Path directory) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[8192];
            int read = process.getInputStream().read(buffer);
            while (read >= 0) {
                output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                read = process.getInputStream().read(buffer);
            }
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, output.toString());
        } catch (IOException exception) {
            throw new NativeSmokeException(
                    "Could not run native smoke command. Check that the native binary is executable.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NativeSmokeException("Native smoke command was interrupted. Try the smoke again.", exception);
        }
    }

    @FunctionalInterface
    interface ProcessRunner {
        ProcessResult run(List<String> command, Path directory);
    }

    record ProcessResult(int exitCode, String output) {
    }
}
