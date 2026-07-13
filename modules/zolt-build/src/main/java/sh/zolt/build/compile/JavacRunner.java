package sh.zolt.build.compile;

import sh.zolt.build.JavacException;
import sh.zolt.classpath.Classpath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public final class JavacRunner {
    private static final Pattern MISSING_PACKAGE_PATTERN =
            Pattern.compile("package \\S+ does not exist");

    private final String pathSeparator;
    private final ProcessRunner processRunner;
    private final InProcessRunner inProcessRunner;
    private final Path runtimeJavac;

    public JavacRunner() {
        this(
                java.io.File.pathSeparator,
                JavacRunner::runProcess,
                JavacRunner::runInProcess,
                runtimeJavac());
    }

    JavacRunner(String pathSeparator, ProcessRunner processRunner) {
        this(pathSeparator, processRunner, null, null);
    }

    JavacRunner(
            String pathSeparator,
            ProcessRunner processRunner,
            InProcessRunner inProcessRunner,
            Path runtimeJavac) {
        this.pathSeparator = pathSeparator;
        this.processRunner = processRunner;
        this.inProcessRunner = inProcessRunner;
        this.runtimeJavac = runtimeJavac;
    }

    public JavacResult compile(
            Path javac,
            List<Path> sources,
            Classpath classpath,
            Path outputDirectory) {
        return compile(javac, sources, classpath, outputDirectory, new Classpath(List.of()), null);
    }

    public JavacResult compile(
            Path javac,
            List<Path> sources,
            Classpath classpath,
            Path outputDirectory,
            Classpath processorClasspath,
            Path generatedSourcesDirectory) {
        return compile(
                javac,
                sources,
                classpath,
                outputDirectory,
                processorClasspath,
                generatedSourcesDirectory,
                JavacOptions.empty());
    }

    public JavacResult compile(
            Path javac,
            List<Path> sources,
            Classpath classpath,
            Path outputDirectory,
            Classpath processorClasspath,
            Path generatedSourcesDirectory,
            JavacOptions options) {
        JavacOptions effectiveOptions = options == null ? JavacOptions.empty() : options;
        List<Path> sortedSources = sources.stream().map(Path::normalize).sorted().toList();
        Path effectiveGeneratedSourcesDirectory = sortedEntries(processorClasspath).isEmpty()
                ? null
                : generatedSourcesDirectory;
        try {
            Files.createDirectories(outputDirectory);
            if (effectiveGeneratedSourcesDirectory != null) {
                Files.createDirectories(effectiveGeneratedSourcesDirectory);
            }
        } catch (IOException exception) {
            throw new JavacException(
                    sh.zolt.error.ActionableError.of(
                            "Could not create compilation output directories for " + outputDirectory + ".",
                            "Check that the project directory is writable.",
                            exception));
        }
        if (sortedSources.isEmpty()) {
            return new JavacResult(0, outputDirectory, "");
        }

        List<String> command = command(
                javac,
                sortedSources,
                classpath,
                outputDirectory,
                processorClasspath,
                effectiveGeneratedSourcesDirectory,
                effectiveOptions);
        ProcessResult result = canRunInProcess(javac, processorClasspath, effectiveOptions)
                ? inProcessRunner.run(command.subList(1, command.size()))
                : processRunner.run(command);
        if (result.exitCode() != 0) {
            String diagnostics = result.output().stripTrailing();
            String summary = "javac failed with exit code " + result.exitCode() + "."
                    + (diagnostics.isEmpty() ? "" : "\n" + diagnostics);
            throw new JavacException(sh.zolt.error.ActionableError.of(summary, remediation(diagnostics)));
        }
        return new JavacResult(sortedSources.size(), outputDirectory, result.output());
    }

    private boolean canRunInProcess(Path javac, Classpath processorClasspath, JavacOptions options) {
        return inProcessRunner != null
                && runtimeJavac != null
                && sameExecutable(javac, runtimeJavac)
                && sortedEntries(processorClasspath).isEmpty()
                && options.arguments().stream().noneMatch(argument -> argument.startsWith("-J"));
    }

    private static String remediation(String diagnostics) {
        if (isMissingDependencyFailure(diagnostics)) {
            return "Fix the Java compilation errors and try again. "
                    + "A referenced package could not be found, so a required dependency is likely "
                    + "missing or unresolved: check the declared dependencies and zolt.lock.";
        }
        return "Fix the Java compilation errors and try again. "
                + "If annotation processing is configured, inspect [annotationProcessors], "
                + "[test.annotationProcessors], and processor-scoped entries in zolt.lock.";
    }

    private static boolean isMissingDependencyFailure(String diagnostics) {
        return MISSING_PACKAGE_PATTERN.matcher(diagnostics).find();
    }

    private List<String> command(
            Path javac,
            List<Path> sources,
            Classpath classpath,
            Path outputDirectory,
            Classpath processorClasspath,
            Path generatedSourcesDirectory,
            JavacOptions options) {
        List<String> command = new ArrayList<>();
        command.add(javac.toString());
        command.add("-d");
        command.add(outputDirectory.toString());
        if (!options.release().isBlank()) {
            if (options.hostPlatformApi()) {
                // Host mode: target bytecode N but compile against the build JDK's platform API,
                // matching legacy Maven `-source/-target`. Not reproducible across build JDKs.
                command.add("-source");
                command.add(options.release());
                command.add("-target");
                command.add(options.release());
            } else {
                // Default: --release N pins the platform API to Java N via ct.sym, reproducible
                // across build JDKs.
                command.add("--release");
                command.add(options.release());
            }
        }
        if (!options.encoding().isBlank()) {
            command.add("-encoding");
            command.add(options.encoding());
        }
        List<Path> modulePathEntries = sortedModulePath(options);
        List<Path> classpathEntries = classpathWithoutModulePath(sortedEntries(classpath), modulePathEntries);
        if (!classpathEntries.isEmpty()) {
            command.add("-classpath");
            command.add(joinedPath(classpathEntries));
        }
        if (!modulePathEntries.isEmpty()) {
            command.add("--module-path");
            command.add(joinedPath(modulePathEntries));
        }
        List<Path> processorClasspathEntries = sortedEntries(processorClasspath);
        if (processorClasspathEntries.isEmpty()) {
            command.add("-proc:none");
        } else {
            command.add("-processorpath");
            command.add(joinedPath(combinedProcessorPath(processorClasspathEntries, classpathEntries)));
        }
        if (generatedSourcesDirectory != null) {
            command.add("-s");
            command.add(generatedSourcesDirectory.toString());
        }
        command.addAll(options.arguments());
        for (Path source : sources) {
            command.add(source.toString());
        }
        return List.copyOf(command);
    }

    private static List<Path> sortedModulePath(JavacOptions options) {
        if (options == null || options.modulePath().isEmpty()) {
            return List.of();
        }
        return options.modulePath().stream()
                .map(Path::normalize)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static List<Path> classpathWithoutModulePath(List<Path> classpathEntries, List<Path> modulePathEntries) {
        if (modulePathEntries.isEmpty()) {
            return classpathEntries;
        }
        LinkedHashSet<Path> moduleEntries = new LinkedHashSet<>(modulePathEntries);
        return classpathEntries.stream()
                .filter(entry -> !moduleEntries.contains(entry))
                .toList();
    }

    private static List<Path> sortedEntries(Classpath classpath) {
        if (classpath == null) {
            return List.of();
        }
        return classpath.entries().stream()
                .map(Path::normalize)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String joinedPath(List<Path> entries) {
        StringJoiner joiner = new StringJoiner(pathSeparator);
        for (Path entry : entries) {
            joiner.add(entry.toString());
        }
        return joiner.toString();
    }

    private static List<Path> combinedProcessorPath(List<Path> processorEntries, List<Path> classpathEntries) {
        LinkedHashSet<Path> entries = new LinkedHashSet<>();
        entries.addAll(processorEntries);
        entries.addAll(classpathEntries);
        return List.copyOf(entries);
    }

    private static ProcessResult runProcess(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, output);
        } catch (IOException exception) {
            throw new JavacException(
                    sh.zolt.error.ActionableError.of(
                            "Could not run javac.",
                            "Check that the configured JDK is installed and readable.",
                            exception));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new JavacException(
                    sh.zolt.error.ActionableError.of(
                            "javac was interrupted.", "Try the build again.", exception));
        }
    }

    private static ProcessResult runInProcess(List<String> arguments) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return runProcess(withJavacExecutable(runtimeJavac(), arguments));
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exitCode = compiler.run(
                null,
                output,
                output,
                arguments.toArray(String[]::new));
        return new ProcessResult(exitCode, output.toString(StandardCharsets.UTF_8));
    }

    private static List<String> withJavacExecutable(Path javac, List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add(javac.toString());
        command.addAll(arguments);
        return List.copyOf(command);
    }

    private static Path runtimeJavac() {
        return runtimeJavac(
                System.getProperty("java.home"),
                System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    static Path runtimeJavac(String javaHome, String nativeImageCode) {
        if (nativeImageCode != null || javaHome == null || javaHome.isBlank()) {
            return null;
        }
        return Path.of(javaHome)
                .resolve("bin")
                .resolve(executable("javac"));
    }

    private static boolean sameExecutable(Path left, Path right) {
        Path normalizedLeft = left.toAbsolutePath().normalize();
        Path normalizedRight = right.toAbsolutePath().normalize();
        if (Files.exists(normalizedLeft) && Files.exists(normalizedRight)) {
            try {
                return Files.isSameFile(normalizedLeft, normalizedRight);
            } catch (IOException exception) {
                return false;
            }
        }
        return normalizedLeft.equals(normalizedRight);
    }

    private static String executable(String name) {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? name + ".exe"
                : name;
    }

    @FunctionalInterface
    interface ProcessRunner {
        ProcessResult run(List<String> command);
    }

    @FunctionalInterface
    interface InProcessRunner {
        ProcessResult run(List<String> arguments);
    }

    record ProcessResult(int exitCode, String output) {
    }
}
