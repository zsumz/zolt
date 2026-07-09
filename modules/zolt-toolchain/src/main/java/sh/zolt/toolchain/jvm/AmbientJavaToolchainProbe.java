package sh.zolt.toolchain.jvm;

import sh.zolt.project.toolchain.JavaFeature;
import sh.zolt.project.toolchain.JavaToolchainRequest;
import sh.zolt.project.toolchain.ToolchainPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AmbientJavaToolchainProbe implements JavaToolchainProbe {
    private static final Pattern VERSION_LINE = Pattern.compile("version \"([^\"]+)\"");
    private static final Pattern PROPERTY_LINE = Pattern.compile("\\s*([^=]+?)\\s*=\\s*(.+)");

    private final Function<String, String> environment;
    private final String pathSeparator;
    private final String osName;
    private final Optional<Path> runtimeJavaHome;
    private final RuntimeInfoReader runtimeInfoReader;
    private volatile AmbientTools cachedTools;

    public AmbientJavaToolchainProbe() {
        this(
                System::getenv,
                java.io.File.pathSeparator,
                System.getProperty("os.name"),
                runtimeJavaHome(System.getProperty("java.home")),
                AmbientJavaToolchainProbe::readRuntimeInfo);
    }

    AmbientJavaToolchainProbe(
            Function<String, String> environment,
            String pathSeparator,
            String osName,
            Optional<Path> runtimeJavaHome,
            RuntimeInfoReader runtimeInfoReader) {
        this.environment = environment;
        this.pathSeparator = pathSeparator;
        this.osName = osName;
        this.runtimeJavaHome = runtimeJavaHome == null ? Optional.empty() : runtimeJavaHome;
        this.runtimeInfoReader = runtimeInfoReader;
    }

    @Override
    public ResolvedJavaToolchain resolve(JavaToolchainRequest request) {
        AmbientTools tools = ambientTools();
        List<String> problems = problems(
                request,
                tools.java(),
                tools.javac(),
                tools.jar(),
                tools.nativeImage(),
                tools.runtime());
        List<String> notes = notes(request);
        return new ResolvedJavaToolchain(
                JavaToolchainSource.AMBIENT,
                tools.javaHome(),
                tools.java(),
                tools.javac(),
                tools.jar(),
                tools.nativeImage(),
                tools.runtime(),
                request,
                problems,
                notes);
    }

    private AmbientTools ambientTools() {
        AmbientTools tools = cachedTools;
        if (tools != null) {
            return tools;
        }
        synchronized (this) {
            if (cachedTools == null) {
                cachedTools = detectAmbientTools();
            }
            return cachedTools;
        }
    }

    private AmbientTools detectAmbientTools() {
        Optional<Path> configuredJavaHome = value("JAVA_HOME").map(Path::of);
        Optional<Path> java = findTool("java", configuredJavaHome);
        Optional<Path> javac = findTool("javac", configuredJavaHome);
        Optional<Path> jar = findTool("jar", configuredJavaHome);
        Optional<Path> nativeImage = findTool("native-image", configuredJavaHome);
        JavaRuntimeInfo runtime = java.flatMap(runtimeInfoReader::read).orElse(JavaRuntimeInfo.empty());
        return new AmbientTools(
                selectedJavaHome(configuredJavaHome, java),
                java,
                javac,
                jar,
                nativeImage,
                runtime);
    }

    static Optional<String> featureVersion(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.strip();
        String[] parts = normalized.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return Optional.of(parts[1]);
        }
        return Optional.of(parts[0]);
    }

    static Optional<Path> runtimeJavaHome(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(Path.of(value));
    }

    static JavaRuntimeInfo parseRuntimeInfo(String output) {
        Optional<String> version = versionFromOutput(output);
        return new JavaRuntimeInfo(
                version,
                version.flatMap(AmbientJavaToolchainProbe::featureVersion),
                property(output, "java.vendor"));
    }

    private Optional<Path> findTool(String name, Optional<Path> configuredJavaHome) {
        String executable = executableName(name);
        Optional<Path> fromConfigured = configuredJavaHome
                .map(home -> home.resolve("bin").resolve(executable))
                .filter(this::isUsable);
        if (fromConfigured.isPresent()) {
            return fromConfigured;
        }
        Optional<Path> fromRuntime = runtimeJavaHome
                .map(home -> home.resolve("bin").resolve(executable))
                .filter(this::isUsable);
        if (fromRuntime.isPresent()) {
            return fromRuntime;
        }
        return value("PATH").flatMap(path -> {
            for (String entry : path.split(Pattern.quote(pathSeparator))) {
                if (entry.isBlank()) {
                    continue;
                }
                Path candidate = Path.of(entry).resolve(executable);
                if (isUsable(candidate)) {
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        });
    }

    private Optional<Path> selectedJavaHome(Optional<Path> configuredJavaHome, Optional<Path> java) {
        if (configuredJavaHome.isPresent()) {
            return configuredJavaHome;
        }
        if (runtimeJavaHome.isPresent()
                && java.map(path -> path.toAbsolutePath().normalize().startsWith(runtimeJavaHome.orElseThrow()))
                        .orElse(false)) {
            return runtimeJavaHome;
        }
        return java.flatMap(AmbientJavaToolchainProbe::inferJavaHome);
    }

    private List<String> problems(
            JavaToolchainRequest request,
            Optional<Path> java,
            Optional<Path> javac,
            Optional<Path> jar,
            Optional<Path> nativeImage,
            JavaRuntimeInfo runtime) {
        List<String> problems = new ArrayList<>();
        if (request.policy() == ToolchainPolicy.REQUIRE_MANAGED) {
            problems.add("This project requires a Zolt-managed Java toolchain, but ambient Java was selected. Run `zolt toolchain status` for details, then `zolt toolchain sync`.");
        }
        if (java.isEmpty()) {
            problems.add("Missing `java`. Install a JDK, set JAVA_HOME, or configure [toolchain.java] and run `zolt toolchain sync`.");
        }
        if (javac.isEmpty()) {
            problems.add("Missing `javac`. Install a JDK, set JAVA_HOME, or configure [toolchain.java] and run `zolt toolchain sync`.");
        }
        if (jar.isEmpty()) {
            problems.add("Missing `jar`. Install a JDK, set JAVA_HOME, or configure [toolchain.java] and run `zolt toolchain sync`.");
        }
        if (java.isPresent() && runtime.featureVersion().isEmpty()) {
            problems.add("Could not determine Java version. Check that `java -version` runs successfully.");
        }
        if (runtime.featureVersion().isPresent() && !versionSatisfies(runtime.featureVersion().orElseThrow(), request.version())) {
            problems.add("Java version mismatch. Project requests "
                    + request.version()
                    + " or newer but detected "
                    + runtime.featureVersion().orElseThrow()
                    + ".");
        }
        if (request.requiresNativeImage() && nativeImage.isEmpty()) {
            problems.add("Native Image is missing from the resolved Java toolchain. Run `zolt toolchain status`, then `zolt toolchain sync`, or pass --native-image as an explicit override.");
        }
        return List.copyOf(problems);
    }

    private static List<String> notes(JavaToolchainRequest request) {
        if (request.distribution().isPresent()) {
            return List.of("Distribution matching is enforced only for managed toolchains; ambient fallback checks Java version and requested tools.");
        }
        return List.of();
    }

    private static boolean versionSatisfies(String detected, String requested) {
        Optional<Integer> detectedFeature = integerFeature(detected);
        Optional<Integer> requestedFeature = integerFeature(requested);
        if (detectedFeature.isPresent() && requestedFeature.isPresent()) {
            return detectedFeature.orElseThrow() >= requestedFeature.orElseThrow();
        }
        return detected.equals(requested);
    }

    private static Optional<Integer> integerFeature(String value) {
        Optional<String> feature = featureVersion(value);
        if (feature.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(feature.orElseThrow()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> value(String key) {
        String value = environment.apply(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private boolean isUsable(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }

    private String executableName(String name) {
        if (osName.toLowerCase(Locale.ROOT).contains("win")) {
            return name + ".exe";
        }
        return name;
    }

    private static Optional<Path> inferJavaHome(Path java) {
        Path bin = java.toAbsolutePath().normalize().getParent();
        if (bin == null || !"bin".equals(bin.getFileName().toString())) {
            return Optional.empty();
        }
        return Optional.ofNullable(bin.getParent());
    }

    private static Optional<JavaRuntimeInfo> readRuntimeInfo(Path java) {
        try {
            Process process = new ProcessBuilder(java.toString(), "-XshowSettings:properties", "-version")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return exitCode == 0 ? Optional.of(parseRuntimeInfo(output)) : Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static Optional<String> versionFromOutput(String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }
        Matcher line = VERSION_LINE.matcher(output);
        if (line.find()) {
            return Optional.of(line.group(1));
        }
        return property(output, "java.version");
    }

    private static Optional<String> property(String output, String key) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }
        for (String line : output.lines().toList()) {
            Matcher matcher = PROPERTY_LINE.matcher(line);
            if (matcher.matches() && key.equals(matcher.group(1).strip())) {
                return Optional.of(matcher.group(2).strip());
            }
        }
        return Optional.empty();
    }

    @FunctionalInterface
    interface RuntimeInfoReader {
        Optional<JavaRuntimeInfo> read(Path java);
    }

    private record AmbientTools(
            Optional<Path> javaHome,
            Optional<Path> java,
            Optional<Path> javac,
            Optional<Path> jar,
            Optional<Path> nativeImage,
            JavaRuntimeInfo runtime) {
        private AmbientTools {
            javaHome = javaHome == null ? Optional.empty() : javaHome;
            java = java == null ? Optional.empty() : java;
            javac = javac == null ? Optional.empty() : javac;
            jar = jar == null ? Optional.empty() : jar;
            nativeImage = nativeImage == null ? Optional.empty() : nativeImage;
            runtime = runtime == null ? JavaRuntimeInfo.empty() : runtime;
        }
    }
}
