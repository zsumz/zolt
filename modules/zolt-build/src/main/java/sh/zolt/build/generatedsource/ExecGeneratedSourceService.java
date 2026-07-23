package sh.zolt.build.generatedsource;

import static sh.zolt.build.generatedsource.ExecGeneratedSourcePaths.outputPath;

import sh.zolt.build.BuildException;
import sh.zolt.classpath.ResolvedClasspathPackage;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.doctor.JdkChecker;
import sh.zolt.doctor.JdkDetector;
import sh.zolt.doctor.JdkStatus;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Runs {@code kind = "exec"} generated-source steps: schedules them from declared IO, then, per step,
 * launches {@code <managed-java> -cp <locked tool jars> <mainClass> <args>} in a sandboxed cwd with a
 * curated environment, content-fingerprint skipping unchanged steps. Output routing (compile source
 * roots vs resource copying) and the consumer fence live in the build wiring, not here.
 */
public final class ExecGeneratedSourceService {
    private static final int LOG_TAIL_LINES = 20;

    private final JdkChecker jdkDetector;
    private final ExecCommandBuilder commandBuilder;
    private final ProcessRunner processRunner;

    public ExecGeneratedSourceService() {
        this(new JdkDetector());
    }

    public ExecGeneratedSourceService(JdkChecker jdkDetector) {
        this(jdkDetector, java.io.File.pathSeparator, ExecGeneratedSourceService::runProcess);
    }

    ExecGeneratedSourceService(JdkChecker jdkDetector, String pathSeparator, ProcessRunner processRunner) {
        this.jdkDetector = jdkDetector;
        this.commandBuilder = new ExecCommandBuilder(pathSeparator);
        this.processRunner = processRunner;
    }

    public void generateMain(Path projectDirectory, ProjectConfig config, List<ResolvedClasspathPackage> packages) {
        generate(projectDirectory, config, packages, "main", config.build().generatedMainSources());
    }

    public void generateTest(Path projectDirectory, ProjectConfig config, List<ResolvedClasspathPackage> packages) {
        generate(projectDirectory, config, packages, "test", config.build().generatedTestSources());
    }

    private void generate(
            Path projectDirectory,
            ProjectConfig config,
            List<ResolvedClasspathPackage> packages,
            String scope,
            List<GeneratedSourceStep> steps) {
        List<GeneratedSourceStep> execSteps = steps.stream()
                .filter(step -> step.kind() == GeneratedSourceKind.EXEC)
                .toList();
        if (execSteps.isEmpty()) {
            return;
        }
        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new BuildException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }
        List<Path> toolClasspath = toolClasspath(packages);
        if (toolClasspath.isEmpty()) {
            throw BuildException.actionable(
                    "Exec generation requires locked tool artifacts in scope `tool-exec`, "
                            + "but zolt.lock does not contain them.",
                    "Run `zolt resolve` to refresh zolt.lock, then retry `zolt build`.");
        }
        Path root = ProjectPaths.root(projectDirectory);
        String outputRoot = config.build().outputRoot();
        ExecGeneratedSourceCache cache = new ExecGeneratedSourceCache(
                root.resolve(outputRoot).resolve(".zolt").resolve("exec"));
        List<GeneratedSourceStep> ordered = ExecStepScheduler.order(root, outputRoot, scope, execSteps);
        Path javaExecutable = jdkStatus.java().orElseThrow();
        for (GeneratedSourceStep step : ordered) {
            generateStep(root, javaExecutable, toolClasspath, scope, step, cache);
        }
    }

    private void generateStep(
            Path projectRoot,
            Path javaExecutable,
            List<Path> toolClasspath,
            String scope,
            GeneratedSourceStep step,
            ExecGeneratedSourceCache cache) {
        ExecGeneratedSourceValidator.validateStep(projectRoot, scope, step);
        Path output = outputPath(projectRoot, step.output(), scope, step.id(), "output");
        ExecGeneratedSourceCache.GenerationCacheState cacheState =
                cache.state(projectRoot, output, toolClasspath, scope, step);
        if (cache.isCurrent(output, cacheState)) {
            return;
        }
        if (step.clean()) {
            deleteOutput(output);
        }
        createDirectory(output);
        List<String> command = commandBuilder.command(javaExecutable, toolClasspath, step);
        Map<String, String> environment = ExecEnvironment.build(projectRoot, output, scope, step);
        ProcessResult result = processRunner.run(command, projectRoot, environment);
        cache.writeLog(cacheState, result.output());
        if (result.exitCode() != 0) {
            throw new BuildException(
                    "Exec step [generated." + scope + "." + step.id() + "] (tool `" + step.exec().toolName()
                            + "`) failed with exit code " + result.exitCode() + ". Review " + cacheState.log()
                            + ", fix the inputs or tool arguments, and retry `zolt build`.\n" + logTail(result.output()));
        }
        cache.writeFingerprint(cacheState);
    }

    private static List<Path> toolClasspath(List<ResolvedClasspathPackage> packages) {
        return packages.stream()
                .filter(dependency -> dependency.scope() == DependencyScope.TOOL_EXEC)
                .map(dependency -> dependency.resolvedPackage().jarPath())
                .distinct()
                .sorted()
                .toList();
    }

    private static String logTail(String output) {
        String[] lines = output.stripTrailing().split("\n", -1);
        if (lines.length <= LOG_TAIL_LINES) {
            return String.join("\n", lines);
        }
        return String.join("\n", List.of(lines).subList(lines.length - LOG_TAIL_LINES, lines.length));
    }

    private static void deleteOutput(Path output) {
        if (!Files.exists(output)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(output)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not clean exec output " + output + ". Check filesystem permissions and retry `zolt build`.",
                    exception);
        }
    }

    private static void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not create exec output directory " + path + ". Check filesystem permissions.", exception);
        }
    }

    private static ProcessResult runProcess(List<String> command, Path directory, Map<String, String> environment) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true);
            processBuilder.environment().clear();
            processBuilder.environment().putAll(environment);
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, output);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not run exec tool. Check that the configured JDK can launch Java processes.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BuildException("Exec generation was interrupted. Try `zolt build` again.", exception);
        }
    }

    @FunctionalInterface
    interface ProcessRunner {
        ProcessResult run(List<String> command, Path directory, Map<String, String> environment);
    }

    record ProcessResult(int exitCode, String output) {
    }
}
