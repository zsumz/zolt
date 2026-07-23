package sh.zolt.build.generatedsource;

import static sh.zolt.build.generatedsource.ExecGeneratedSourcePaths.outputPath;

import sh.zolt.build.BuildException;
import sh.zolt.build.generatedsource.ExecGeneratedSourceCache.ExecToolIdentity;
import sh.zolt.build.generatedsource.ExecGeneratedSourceCache.GenerationCacheState;
import sh.zolt.classpath.ResolvedClasspathPackage;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.doctor.JdkChecker;
import sh.zolt.doctor.JdkDetector;
import sh.zolt.doctor.JdkStatus;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectPaths;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Runs {@code kind = "exec"} generated-source steps. Position is derived from declared IO: pre-compile
 * steps (java/test sources, resources, intermediate over source inputs) run before compilation via
 * {@link #generateMain}/{@link #generateTest}; post-compile steps ({@code tool = "project"} or an input
 * under compiled classes) run after compilation via the {@code *PostCompile} entry points. Each step
 * launches its runner (jvm/project over a classpath, process over a curated-PATH binary) in a sandboxed
 * cwd with a curated environment, content-fingerprint skipping unchanged steps ({@code cache = "none"}
 * always runs). Output routing and the consumer fence live in the build wiring, not here.
 */
public final class ExecGeneratedSourceService {
    private static final int LOG_TAIL_LINES = 20;
    private static final int DESTROY_GRACE_SECONDS = 3;

    private final JdkChecker jdkDetector;
    private final ExecCommandBuilder commandBuilder;
    private final ProcessRunner processRunner;
    private final String pathSeparator;
    private final UnaryOperator<String> ambientEnv;

    public ExecGeneratedSourceService() {
        this(new JdkDetector());
    }

    public ExecGeneratedSourceService(JdkChecker jdkDetector) {
        this(jdkDetector, java.io.File.pathSeparator, ExecGeneratedSourceService::runProcess);
    }

    ExecGeneratedSourceService(JdkChecker jdkDetector, String pathSeparator, ProcessRunner processRunner) {
        this(jdkDetector, pathSeparator, processRunner, System::getenv);
    }

    ExecGeneratedSourceService(
            JdkChecker jdkDetector,
            String pathSeparator,
            ProcessRunner processRunner,
            UnaryOperator<String> ambientEnv) {
        this.jdkDetector = jdkDetector;
        this.commandBuilder = new ExecCommandBuilder(pathSeparator);
        this.processRunner = processRunner;
        this.pathSeparator = pathSeparator;
        this.ambientEnv = ambientEnv;
    }

    public void generateMain(Path projectDirectory, ProjectConfig config, List<ResolvedClasspathPackage> packages) {
        generateMain(projectDirectory, config, packages, false);
    }

    public void generateMain(
            Path projectDirectory, ProjectConfig config, List<ResolvedClasspathPackage> packages, boolean offline) {
        run(projectDirectory, config, packages, "main", config.build().generatedMainSources(), Phase.PRE_COMPILE, offline);
    }

    public void generateMainPostCompile(
            Path projectDirectory, ProjectConfig config, List<ResolvedClasspathPackage> packages, boolean offline) {
        run(projectDirectory, config, packages, "main", config.build().generatedMainSources(), Phase.POST_COMPILE, offline);
    }

    public void generateTest(Path projectDirectory, ProjectConfig config, List<ResolvedClasspathPackage> packages) {
        generateTest(projectDirectory, config, packages, false);
    }

    public void generateTest(
            Path projectDirectory, ProjectConfig config, List<ResolvedClasspathPackage> packages, boolean offline) {
        run(projectDirectory, config, packages, "test", config.build().generatedTestSources(), Phase.PRE_COMPILE, offline);
    }

    public void generateTestPostCompile(
            Path projectDirectory, ProjectConfig config, List<ResolvedClasspathPackage> packages, boolean offline) {
        run(projectDirectory, config, packages, "test", config.build().generatedTestSources(), Phase.POST_COMPILE, offline);
    }

    private void run(
            Path projectDirectory,
            ProjectConfig config,
            List<ResolvedClasspathPackage> packages,
            String scope,
            List<GeneratedSourceStep> steps,
            Phase phase,
            boolean offline) {
        List<GeneratedSourceStep> execSteps = steps.stream()
                .filter(step -> step.kind() == GeneratedSourceKind.EXEC)
                .toList();
        if (execSteps.isEmpty()) {
            return;
        }
        Path root = ProjectPaths.root(projectDirectory);
        String outputRoot = config.build().outputRoot();
        List<GeneratedSourceStep> ordered = ExecStepScheduler.order(root, outputRoot, scope, execSteps);
        List<GeneratedSourceStep> phaseSteps = ordered.stream()
                .filter(step -> phase.matches(ExecStepClassification.isPostCompile(step, root, outputRoot)))
                .toList();
        if (phaseSteps.isEmpty()) {
            return;
        }
        StepContext context = prepareContext(root, config, packages, scope, phaseSteps);
        for (GeneratedSourceStep step : phaseSteps) {
            generateStep(context, step, offline);
        }
    }

    private StepContext prepareContext(
            Path root,
            ProjectConfig config,
            List<ResolvedClasspathPackage> packages,
            String scope,
            List<GeneratedSourceStep> phaseSteps) {
        boolean needsJava = phaseSteps.stream().anyMatch(step -> {
            String runner = step.exec().tool().runner();
            return "jvm".equals(runner) || "project".equals(runner);
        });
        Path javaExecutable = null;
        if (needsJava) {
            JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
            if (!jdkStatus.ok()) {
                throw new BuildException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
            }
            javaExecutable = jdkStatus.java().orElseThrow();
        }
        Path metadataDirectory = root.resolve(config.build().outputRoot()).resolve(".zolt").resolve("exec");
        return new StepContext(
                root,
                config,
                scope,
                javaExecutable,
                toolClasspath(packages),
                projectClasspath(root, config, packages, scope),
                new ExecGeneratedSourceCache(metadataDirectory),
                metadataDirectory);
    }

    private void generateStep(StepContext context, GeneratedSourceStep step, boolean offline) {
        String scope = context.scope();
        ExecGeneratedSourceValidator.validateStep(context.root(), context.config().build().outputRoot(), scope, step);
        String subject = "[generated." + scope + "." + step.id() + "]";
        String cache = step.exec().cache();
        if ("none".equals(cache) && offline) {
            throw BuildException.actionable(
                    "Exec step " + subject + " uses cache = \"none\", which always runs and cannot be satisfied with "
                            + "`--offline`.",
                    "Commit a deterministic input (e.g. checked-in DDL) and use cache = \"content\", or run without "
                            + "--offline.");
        }
        Path cwd = resolveCwd(context.root(), step, subject);
        Path output = outputPath(context.root(), step.output(), scope, step.id(), "output");
        ResolvedCommand resolved = resolveCommand(context, step, subject, cwd);
        GenerationCacheState cacheState =
                context.cache().state(context.root(), cwd, resolved.classpath(), resolved.identity(), scope, step);
        boolean contentCache = "content".equals(cache);
        if (contentCache && context.cache().isCurrent(output, cacheState)) {
            return;
        }
        if (step.clean()) {
            deleteOutput(output);
        }
        createDirectory(output);
        Map<String, String> environment = ExecEnvironment.build(context.root(), output, scope, step, ambientEnv);
        Map<Path, Long> before =
                contentCache ? ExecUndeclaredOutputScan.snapshot(cwd, output, context.metadataDirectory()) : Map.of();
        Duration timeout = Duration.ofSeconds(step.exec().timeoutSeconds());
        ProcessResult result = processRunner.run(resolved.command(), cwd, environment, timeout);
        context.cache().writeLog(cacheState, result.output());
        if (result.timedOut()) {
            throw BuildException.actionable(
                    "Exec step " + subject + " (tool `" + step.exec().toolName() + "`) exceeded its "
                            + step.exec().timeoutSeconds() + "s timeout and was terminated. See " + cacheState.log() + ".",
                    "Raise timeoutSeconds if the tool legitimately needs longer, or fix the step so it completes.");
        }
        if (result.exitCode() != 0) {
            throw new BuildException(
                    "Exec step " + subject + " (tool `" + step.exec().toolName()
                            + "`) failed with exit code " + result.exitCode() + ". Review " + cacheState.log()
                            + ", fix the inputs or tool arguments, and retry `zolt build`.\n" + logTail(result.output()));
        }
        if (contentCache) {
            ExecUndeclaredOutputScan.verify(subject, cwd, output, context.metadataDirectory(), before);
        }
        context.cache().writeFingerprint(cacheState);
    }

    private ResolvedCommand resolveCommand(StepContext context, GeneratedSourceStep step, String subject, Path cwd) {
        List<String> args = step.exec().args();
        return switch (step.exec().tool().runner()) {
            case "jvm" -> {
                if (context.toolClasspath().isEmpty()) {
                    throw BuildException.actionable(
                            "Exec step " + subject + " uses runner jvm but zolt.lock has no tool-exec artifacts.",
                            "Run `zolt resolve` to refresh zolt.lock, then retry `zolt build`.");
                }
                yield new ResolvedCommand(
                        commandBuilder.jvmCommand(
                                context.javaExecutable(), context.toolClasspath(), step.exec().tool().mainClass(), args),
                        context.toolClasspath(),
                        ExecToolIdentity.none());
            }
            case "project" -> new ResolvedCommand(
                    commandBuilder.projectCommand(
                            context.javaExecutable(), context.projectClasspath(), step.exec().tool().mainClass(), args),
                    context.projectClasspath(),
                    ExecToolIdentity.none());
            case "process" -> {
                ExecProcessToolResolver.Resolved probe = ExecProcessToolResolver.resolve(
                        step.exec().tool(), subject, cwd, ambientEnv, pathSeparator, processRunner,
                        Duration.ofSeconds(step.exec().timeoutSeconds()));
                yield new ResolvedCommand(
                        commandBuilder.processCommand(probe.binary(), args),
                        List.of(),
                        new ExecToolIdentity(step.exec().tool().binary(), probe.probedVersion()));
            }
            default -> throw BuildException.actionable(
                    "Exec step " + subject + " uses unsupported runner `" + step.exec().tool().runner() + "`.",
                    "Use runner = \"jvm\" or \"process\", or tool = \"project\".");
        };
    }

    private static Path resolveCwd(Path root, GeneratedSourceStep step, String subject) {
        if (step.exec().cwd().isEmpty()) {
            return root;
        }
        Path resolved = root.resolve(step.exec().cwd().orElseThrow()).normalize();
        if (!resolved.startsWith(root)) {
            throw BuildException.actionable(
                    "Exec step " + subject + " cwd `" + step.exec().cwd().orElseThrow()
                            + "` escapes the project directory.",
                    "Use a project-relative directory under the project root.");
        }
        if (!Files.isDirectory(resolved)) {
            throw BuildException.actionable(
                    "Exec step " + subject + " cwd `" + step.exec().cwd().orElseThrow() + "` is not an existing directory.",
                    "Create the directory or fix " + subject + ".cwd.");
        }
        return resolved;
    }

    private static List<Path> toolClasspath(List<ResolvedClasspathPackage> packages) {
        return packages.stream()
                .filter(dependency -> dependency.scope() == DependencyScope.TOOL_EXEC)
                .map(dependency -> dependency.resolvedPackage().jarPath())
                .distinct()
                .sorted()
                .toList();
    }

    private static List<Path> projectClasspath(
            Path root, ProjectConfig config, List<ResolvedClasspathPackage> packages, String scope) {
        List<Path> entries = new ArrayList<>();
        if ("test".equals(scope)) {
            entries.add(root.resolve(config.build().testOutput()).normalize());
            entries.add(root.resolve(config.build().output()).normalize());
            packages.stream()
                    .filter(dependency -> dependency.scope().entersTestClasspath())
                    .map(dependency -> dependency.resolvedPackage().jarPath())
                    .forEach(entries::add);
        } else {
            entries.add(root.resolve(config.build().output()).normalize());
            packages.stream()
                    .filter(dependency -> dependency.scope().entersMainRuntimeClasspath())
                    .map(dependency -> dependency.resolvedPackage().jarPath())
                    .forEach(entries::add);
        }
        return entries.stream().distinct().toList();
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

    private static ProcessResult runProcess(
            List<String> command, Path directory, Map<String, String> environment, Duration timeout) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true);
            processBuilder.environment().clear();
            processBuilder.environment().putAll(environment);
            Process process = processBuilder.start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Thread pump = new Thread(() -> pump(process, buffer));
            pump.setDaemon(true);
            pump.start();
            boolean finished = process.waitFor(Math.max(1L, timeout.toSeconds()), java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(DESTROY_GRACE_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                process.waitFor();
                joinQuietly(pump);
                return new ProcessResult(-1, buffer.toString(StandardCharsets.UTF_8), true);
            }
            joinQuietly(pump);
            return new ProcessResult(process.exitValue(), buffer.toString(StandardCharsets.UTF_8), false);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not run exec tool. Check that the configured tool can launch processes.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BuildException("Exec generation was interrupted. Try `zolt build` again.", exception);
        }
    }

    private static void pump(Process process, ByteArrayOutputStream buffer) {
        try (InputStream in = process.getInputStream()) {
            in.transferTo(buffer);
        } catch (IOException ignored) {
            // process terminated; whatever was buffered is the log tail.
        }
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private enum Phase {
        PRE_COMPILE,
        POST_COMPILE;

        boolean matches(boolean postCompile) {
            return postCompile == (this == POST_COMPILE);
        }
    }

    private record StepContext(
            Path root,
            ProjectConfig config,
            String scope,
            Path javaExecutable,
            List<Path> toolClasspath,
            List<Path> projectClasspath,
            ExecGeneratedSourceCache cache,
            Path metadataDirectory) {
    }

    private record ResolvedCommand(List<String> command, List<Path> classpath, ExecToolIdentity identity) {
    }

    @FunctionalInterface
    interface ProcessRunner {
        ProcessResult run(List<String> command, Path directory, Map<String, String> environment, Duration timeout);
    }

    record ProcessResult(int exitCode, String output, boolean timedOut) {
    }
}
