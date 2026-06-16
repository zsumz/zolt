package com.zolt.build;

import static com.zolt.build.OpenApiGeneratedSourcePaths.safeProjectPath;

import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.dependency.DependencyScope;
import com.zolt.classpath.ResolvedClasspathPackage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

public final class OpenApiGeneratedSourceService {
    private static final String FINGERPRINT_VERSION = "1";

    private final JdkChecker jdkDetector;
    private final OpenApiGeneratorCommandBuilder commandBuilder;
    private final ProcessRunner processRunner;

    public OpenApiGeneratedSourceService() {
        this(new JdkDetector());
    }

    public OpenApiGeneratedSourceService(JdkChecker jdkDetector) {
        this(jdkDetector, java.io.File.pathSeparator, OpenApiGeneratedSourceService::runProcess);
    }

    OpenApiGeneratedSourceService(
            JdkChecker jdkDetector,
            String pathSeparator,
            ProcessRunner processRunner) {
        this.jdkDetector = jdkDetector;
        this.commandBuilder = new OpenApiGeneratorCommandBuilder(pathSeparator);
        this.processRunner = processRunner;
    }

    public void generateMain(
            Path projectDirectory,
            ProjectConfig config,
            List<ResolvedClasspathPackage> packages) {
        generate(projectDirectory, config, packages, "main", config.build().generatedMainSources());
    }

    public void generateTest(
            Path projectDirectory,
            ProjectConfig config,
            List<ResolvedClasspathPackage> packages) {
        generate(projectDirectory, config, packages, "test", config.build().generatedTestSources());
    }

    private void generate(
            Path projectDirectory,
            ProjectConfig config,
            List<ResolvedClasspathPackage> packages,
            String scope,
            List<GeneratedSourceStep> steps) {
        List<GeneratedSourceStep> openApiSteps = steps.stream()
                .filter(step -> step.kind() == GeneratedSourceKind.OPENAPI)
                .toList();
        if (openApiSteps.isEmpty()) {
            return;
        }
        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new BuildException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }
        List<Path> toolClasspath = toolClasspath(packages);
        if (toolClasspath.isEmpty()) {
            throw new BuildException(
                    "OpenAPI generation requires locked tool artifacts in scope `tool-openapi`. "
                            + "Run `zolt resolve` to refresh zolt.lock, then retry `zolt build`.");
        }
        Path root = projectDirectory.toAbsolutePath().normalize();
        for (GeneratedSourceStep step : openApiSteps) {
            generateStep(root, jdkStatus.java().orElseThrow(), toolClasspath, scope, step);
        }
    }

    private void generateStep(
            Path projectRoot,
            Path javaExecutable,
            List<Path> toolClasspath,
            String scope,
            GeneratedSourceStep step) {
        validateStep(projectRoot, scope, step);
        Path output = safeProjectPath(projectRoot, step.output(), scope, step.id(), "output");
        Path fingerprint = output.resolve(".zolt-openapi-" + scope + "-" + step.id() + ".fingerprint");
        String expectedFingerprint = fingerprint(projectRoot, toolClasspath, scope, step);
        if (Files.isDirectory(output)
                && Files.isRegularFile(fingerprint)
                && readFingerprint(fingerprint).equals(expectedFingerprint)) {
            return;
        }
        deleteOutput(output);
        createDirectory(output);
        List<String> command = commandBuilder.command(projectRoot, javaExecutable, toolClasspath, scope, step);
        ProcessResult result = processRunner.run(command, projectRoot);
        Path log = output.resolve(".zolt-openapi-" + scope + "-" + step.id() + ".log");
        writeLog(log, result.output());
        if (result.exitCode() != 0) {
            throw new BuildException(
                    "OpenAPI generation failed for [generated."
                            + scope
                            + "."
                            + step.id()
                            + "] with exit code "
                            + result.exitCode()
                            + ". Review "
                            + log
                            + ", fix the input or generator options, and retry `zolt build`.\n"
                            + result.output().stripTrailing());
        }
        writeFingerprint(fingerprint, expectedFingerprint);
    }

    private static void validateStep(Path projectRoot, String scope, GeneratedSourceStep step) {
        if (!"java".equals(step.language())) {
            throw new BuildException(
                    "OpenAPI generated source step [generated."
                            + scope
                            + "."
                            + step.id()
                            + "] uses unsupported language `"
                            + step.language()
                            + "`. Zolt currently supports java.");
        }
        if (step.inputs().size() != 1) {
            throw new BuildException(
                    "OpenAPI generated source step [generated."
                            + scope
                            + "."
                            + step.id()
                            + "] requires exactly one input spec.");
        }
        Path input = safeProjectPath(projectRoot, step.inputs().getFirst(), scope, step.id(), "input");
        if (!Files.isRegularFile(input)) {
            throw new BuildException(
                    "OpenAPI input "
                            + step.inputs().getFirst()
                            + " does not exist for [generated."
                            + scope
                            + "."
                            + step.id()
                            + "]. Add the file or remove the generated-source step.");
        }
        OpenApiGenerationSettings settings = step.openApi();
        if (settings.toolCoordinate().isEmpty() || settings.toolVersion().isEmpty()) {
            throw new BuildException(
                    "OpenAPI generation requires [generated.openapiTool].coordinate and version. "
                            + "Add org.openapitools:openapi-generator-cli with version or versionRef, run `zolt resolve`, then retry.");
        }
        if (settings.generator().isEmpty()) {
            throw new BuildException(
                    "OpenAPI generated source step [generated."
                            + scope
                            + "."
                            + step.id()
                            + "] requires generator or preset.generator.");
        }
        settings.config().ifPresent(value -> requireFile(projectRoot, value, scope, step.id(), "config"));
        settings.templateDir().ifPresent(value -> requireDirectory(projectRoot, value, scope, step.id(), "templateDir"));
    }

    private static void requireFile(Path projectRoot, String value, String scope, String id, String field) {
        Path path = safeProjectPath(projectRoot, value, scope, id, field);
        if (!Files.isRegularFile(path)) {
            throw new BuildException(
                    "OpenAPI "
                            + field
                            + " `"
                            + value
                            + "` does not exist for [generated."
                            + scope
                            + "."
                            + id
                            + "]. Add the file or update the generated-source step.");
        }
    }

    private static void requireDirectory(Path projectRoot, String value, String scope, String id, String field) {
        Path path = safeProjectPath(projectRoot, value, scope, id, field);
        if (!Files.isDirectory(path)) {
            throw new BuildException(
                    "OpenAPI "
                            + field
                            + " `"
                            + value
                            + "` does not exist for [generated."
                            + scope
                            + "."
                            + id
                            + "]. Add the directory or update the generated-source step.");
        }
    }

    private static List<Path> toolClasspath(List<ResolvedClasspathPackage> packages) {
        return packages.stream()
                .filter(dependency -> dependency.scope() == DependencyScope.TOOL_OPENAPI)
                .map(dependency -> dependency.resolvedPackage().jarPath())
                .distinct()
                .sorted()
                .toList();
    }

    private static String fingerprint(
            Path projectRoot,
            List<Path> toolClasspath,
            String scope,
            GeneratedSourceStep step) {
        StringBuilder content = new StringBuilder();
        content.append("version=").append(FINGERPRINT_VERSION).append('\n');
        content.append("scope=").append(scope).append('\n');
        content.append("step=").append(step).append('\n');
        content.append("[toolClasspath]\n");
        toolClasspath.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .forEach(path -> content
                        .append(relative(projectRoot, path))
                        .append('|')
                        .append(fileHash(path))
                        .append('\n'));
        content.append("[inputs]\n");
        step.inputs().stream()
                .map(input -> projectRoot.resolve(input).normalize())
                .sorted()
                .forEach(path -> content
                        .append(relative(projectRoot, path))
                        .append('|')
                        .append(fileHash(path))
                        .append('\n'));
        step.openApi().config().ifPresent(value -> content
                .append("config=")
                .append(value)
                .append('|')
                .append(fileHash(projectRoot.resolve(value).normalize()))
                .append('\n'));
        step.openApi().templateDir().ifPresent(value -> content
                .append("templateDir=")
                .append(value)
                .append('|')
                .append(fileHash(projectRoot.resolve(value).normalize()))
                .append('\n'));
        return sha256(content.toString().getBytes(StandardCharsets.UTF_8));
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
                    "Could not clean OpenAPI output "
                            + output
                            + ". Check filesystem permissions and retry `zolt build`.",
                    exception);
        }
    }

    private static void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not create OpenAPI output directory "
                            + path
                            + ". Check filesystem permissions.",
                    exception);
        }
    }

    private static String readFingerprint(Path fingerprint) {
        try {
            return Files.readString(fingerprint);
        } catch (IOException exception) {
            return "";
        }
    }

    private static void writeFingerprint(Path fingerprint, String content) {
        try {
            Files.writeString(fingerprint, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not write OpenAPI generation fingerprint at "
                            + fingerprint
                            + ". Check filesystem permissions.",
                    exception);
        }
    }

    private static void writeLog(Path log, String output) {
        try {
            Files.writeString(log, output, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not write OpenAPI generation log at "
                            + log
                            + ". Check filesystem permissions.",
                    exception);
        }
    }

    private static ProcessResult runProcess(List<String> command, Path directory) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, output);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not run OpenAPI Generator. Check that the configured JDK can launch Java processes.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BuildException("OpenAPI generation was interrupted. Try `zolt build` again.", exception);
        }
    }

    private static String fileHash(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            return directoryHash(normalized);
        }
        if (!Files.isRegularFile(normalized)) {
            return "missing";
        }
        try {
            return sha256(Files.readAllBytes(normalized));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not fingerprint OpenAPI input "
                            + normalized
                            + ". Check that it is readable.",
                    exception);
        }
    }

    private static String directoryHash(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            StringBuilder content = new StringBuilder();
            paths.filter(Files::isRegularFile)
                    .map(Path::normalize)
                    .sorted()
                    .forEach(path -> content
                            .append(directory.relativize(path).toString().replace('\\', '/'))
                            .append('|')
                            .append(fileHash(path))
                            .append('\n'));
            return sha256(content.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not fingerprint OpenAPI directory "
                            + directory
                            + ". Check that it is readable.",
                    exception);
        }
    }

    private static String relative(Path projectRoot, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new BuildException("Could not compute OpenAPI fingerprint because SHA-256 is unavailable.", exception);
        }
    }

    @FunctionalInterface
    interface ProcessRunner {
        ProcessResult run(List<String> command, Path directory);
    }

    record ProcessResult(int exitCode, String output) {
    }
}
