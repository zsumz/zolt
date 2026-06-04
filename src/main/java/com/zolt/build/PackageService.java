package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.PackageId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipException;

public final class PackageService {
    private static final long DETERMINISTIC_ENTRY_TIME = 0L;
    private static final String BOOT_CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String BOOT_LIB_PREFIX = "BOOT-INF/lib/";
    private static final String BOOT_LOADER_PREFIX = "org/springframework/boot/loader/";
    private static final String BOOT_LAUNCHER = "org.springframework.boot.loader.launch.JarLauncher";
    private static final String LEGACY_BOOT_LAUNCHER = "org.springframework.boot.loader.JarLauncher";
    private static final PackageId SPRING_BOOT_PACKAGE = new PackageId("org.springframework.boot", "spring-boot");
    private static final PackageId SPRING_BOOT_LOADER_PACKAGE = new PackageId(
            "org.springframework.boot",
            "spring-boot-loader");

    private final BuildService buildService;
    private final ManifestGenerator manifestGenerator;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;

    public PackageService() {
        this(new BuildService(), new ManifestGenerator(), new ZoltLockfileReader(), new ClasspathBuilder());
    }

    PackageService(
            BuildService buildService,
            ManifestGenerator manifestGenerator,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder) {
        this.buildService = buildService;
        this.manifestGenerator = manifestGenerator;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
    }

    public PackageResult packageJar(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        PackageMode mode = config.packageSettings().mode();
        ensureSupportedPackageMode(mode);
        BuildResult buildResult = buildService.build(projectDirectory, config, cacheRoot);
        return packageJar(projectDirectory, config, buildResult, cacheRoot);
    }

    public PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path cacheRoot) {
        PackageMode mode = config.packageSettings().mode();
        ensureSupportedPackageMode(mode);
        return packageJar(projectDirectory, config, buildResult, Optional.of(cacheRoot));
    }

    public PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult) {
        PackageMode mode = config.packageSettings().mode();
        ensureSupportedPackageMode(mode);
        return packageJar(projectDirectory, config, buildResult, Optional.empty());
    }

    private PackageResult packageJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Optional<Path> cacheRoot) {
        PackageMode mode = config.packageSettings().mode();
        return switch (mode) {
            case THIN -> packageThinJar(projectDirectory, config, buildResult, cacheRoot);
            case SPRING_BOOT -> packageSpringBootJar(
                    projectDirectory,
                    config,
                    buildResult,
                    cacheRoot.orElseThrow(() -> new PackageException(
                            "Spring Boot package mode requires dependency jar access from zolt.lock. Use single-project `zolt package --mode spring-boot` for now; workspace Spring Boot packaging is not wired yet.")));
            case UBER -> throw unsupportedPackageMode(mode);
        };
    }

    private PackageResult packageThinJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Optional<Path> cacheRoot) {
        Path outputDirectory = requireOutputDirectory(buildResult);
        Path jarPath = jarPath(projectDirectory, config);
        Path runtimeClasspathPath = runtimeClasspathPath(jarPath);
        GeneratedManifest manifest = manifestGenerator.generate(config);

        try {
            Files.createDirectories(jarPath.getParent());
            List<Path> files = compiledFiles(outputDirectory);
            try (OutputStream fileOutput = Files.newOutputStream(jarPath);
                    JarOutputStream jarOutput = new JarOutputStream(fileOutput)) {
                writeEntry(jarOutput, manifest.path(), manifest.content());
                for (Path file : files) {
                    writeEntry(jarOutput, entryName(outputDirectory, file), Files.readAllBytes(file));
                }
            }
            Optional<Path> writtenRuntimeClasspathPath = Optional.empty();
            if (cacheRoot.isPresent()) {
                writeRuntimeClasspath(projectDirectory, cacheRoot.orElseThrow(), runtimeClasspathPath);
                writtenRuntimeClasspathPath = Optional.of(runtimeClasspathPath);
            }
            return new PackageResult(
                    buildResult,
                    PackageMode.THIN,
                    jarPath,
                    writtenRuntimeClasspathPath,
                    files.size(),
                    manifest.mainClass().isPresent());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package jar at "
                            + jarPath
                            + ". Check that target/ is writable and try again.",
                    exception);
        }
    }

    private PackageResult packageSpringBootJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Path cacheRoot) {
        String startClass = config.project().main().orElseThrow(() -> new PackageException(
                "Spring Boot package mode requires [project].main in zolt.toml. Add the application main class and retry."));
        Path outputDirectory = requireOutputDirectory(buildResult);
        Path jarPath = jarPath(projectDirectory, config);
        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        List<RuntimeJar> runtimeJars = runtimeJars(lockfile, cacheRoot);
        SpringBootLoader loader = springBootLoader(runtimeJars);

        try {
            Files.createDirectories(jarPath.getParent());
            List<Path> files = compiledFiles(outputDirectory);
            try (OutputStream fileOutput = Files.newOutputStream(jarPath);
                    JarOutputStream jarOutput = new JarOutputStream(fileOutput)) {
                writeEntry(jarOutput, GeneratedManifest.DEFAULT_PATH, springBootManifest(startClass, loader));
                for (Map.Entry<String, byte[]> entry : loader.entries().entrySet()) {
                    writeEntry(jarOutput, entry.getKey(), entry.getValue());
                }
                for (Path file : files) {
                    writeEntry(
                            jarOutput,
                            BOOT_CLASSES_PREFIX + entryName(outputDirectory, file),
                            Files.readAllBytes(file));
                }
                for (RuntimeJar runtimeJar : runtimeJars) {
                    if (runtimeJar.packageId().equals(SPRING_BOOT_LOADER_PACKAGE)) {
                        continue;
                    }
                    writeStoredEntry(
                            jarOutput,
                            BOOT_LIB_PREFIX + nestedJarName(runtimeJar),
                            readRuntimeJar(runtimeJar));
                }
            }
            return new PackageResult(
                    buildResult,
                    PackageMode.SPRING_BOOT,
                    jarPath,
                    Optional.empty(),
                    files.size(),
                    true);
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package Spring Boot jar at "
                            + jarPath
                            + ". Check that target/ is writable and try again.",
                    exception);
        }
    }

    private static void ensureSupportedPackageMode(PackageMode mode) {
        if (mode != PackageMode.UBER) {
            return;
        }
        throw unsupportedPackageMode(mode);
    }

    private static PackageException unsupportedPackageMode(PackageMode mode) {
        return new PackageException(
                "Package mode `"
                        + mode.configValue()
                        + "` is not implemented yet. Use `zolt package --mode thin` or set [package].mode = \"thin\" "
                        + "until uber jar support lands"
                        + ".");
    }

    private Path requireOutputDirectory(BuildResult buildResult) {
        Path outputDirectory = buildResult.outputDirectory();
        if (!Files.isDirectory(outputDirectory)) {
            throw new PackageException(
                    "Build output directory does not exist at "
                            + outputDirectory
                            + ". Run zolt build and check [build].output in zolt.toml.");
        }
        return outputDirectory;
    }

    private static Path jarPath(Path projectDirectory, ProjectConfig config) {
        return projectDirectory
                .resolve("target")
                .resolve(config.project().name() + "-" + config.project().version() + ".jar");
    }

    private void writeRuntimeClasspath(
            Path projectDirectory,
            Path cacheRoot,
            Path runtimeClasspathPath) throws IOException {
        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        String content = classpaths.runtime().entries().stream()
                .map(Path::toString)
                .collect(Collectors.joining("\n"));
        if (!content.isEmpty()) {
            content = content + "\n";
        }
        Files.writeString(runtimeClasspathPath, content);
    }

    private List<RuntimeJar> runtimeJars(ZoltLockfile lockfile, Path cacheRoot) {
        Map<String, RuntimeJar> runtimeJars = new LinkedHashMap<>();
        lockfileReader.classpathPackages(lockfile, cacheRoot).stream()
                .filter(dependency -> dependency.scope().entersMainRuntimeClasspath())
                .sorted(Comparator.comparing(PackageService::classpathSortKey))
                .map(dependency -> new RuntimeJar(
                        dependency.resolvedPackage().packageId(),
                        dependency.resolvedPackage().selectedVersion(),
                        dependency.resolvedPackage().jarPath()))
                .forEach(runtimeJar -> runtimeJars.putIfAbsent(runtimeJarKey(runtimeJar), runtimeJar));
        return List.copyOf(runtimeJars.values());
    }

    private static String classpathSortKey(ResolvedClasspathPackage dependency) {
        return dependency.resolvedPackage().packageId()
                + ":"
                + dependency.resolvedPackage().selectedVersion()
                + ":"
                + dependency.scope();
    }

    private static String runtimeJarKey(RuntimeJar runtimeJar) {
        return runtimeJar.packageId() + ":" + runtimeJar.version() + ":" + runtimeJar.jarPath();
    }

    private static SpringBootLoader springBootLoader(List<RuntimeJar> runtimeJars) {
        RuntimeJar loaderJar = runtimeJars.stream()
                .filter(runtimeJar -> runtimeJar.packageId().equals(SPRING_BOOT_LOADER_PACKAGE))
                .findFirst()
                .orElseThrow(() -> new PackageException(missingSpringBootLoaderMessage(runtimeJars)));
        Map<String, byte[]> entries;
        try {
            entries = loaderEntries(loaderJar.jarPath());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not read Spring Boot loader jar at "
                            + loaderJar.jarPath()
                            + ". Run `zolt resolve` to refresh the artifact cache and retry.",
                    exception);
        }
        if (entries.isEmpty()) {
            throw new PackageException(
                    "Spring Boot loader jar at "
                            + loaderJar.jarPath()
                            + " does not contain "
                            + BOOT_LOADER_PREFIX
                            + " classes. Check the resolved org.springframework.boot:spring-boot-loader artifact.");
        }
        String launcherClass = launcherClass(entries);
        return new SpringBootLoader(loaderJar, launcherClass, entries);
    }

    private static String missingSpringBootLoaderMessage(List<RuntimeJar> runtimeJars) {
        String versionHint = runtimeJars.stream()
                .filter(runtimeJar -> runtimeJar.packageId().equals(SPRING_BOOT_PACKAGE))
                .map(RuntimeJar::version)
                .findFirst()
                .map(version -> " The resolved Spring Boot version appears to be " + version + ".")
                .orElse("");
        return "Spring Boot package mode requires `org.springframework.boot:spring-boot-loader` in zolt.lock. Add the Spring Boot platform to [platforms] so Zolt can resolve the loader as package tooling, or declare the loader with an explicit version, then run `zolt resolve` and retry."
                + versionHint;
    }

    private static Map<String, byte[]> loaderEntries(Path loaderJar) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(loaderJar.toFile())) {
            List<JarEntry> loaderEntries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().startsWith(BOOT_LOADER_PREFIX))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            for (JarEntry entry : loaderEntries) {
                try (var input = jar.getInputStream(entry)) {
                    entries.put(entry.getName(), input.readAllBytes());
                }
            }
        }
        return entries;
    }

    private static String launcherClass(Map<String, byte[]> loaderEntries) {
        if (loaderEntries.containsKey(classEntryName(BOOT_LAUNCHER))) {
            return BOOT_LAUNCHER;
        }
        if (loaderEntries.containsKey(classEntryName(LEGACY_BOOT_LAUNCHER))) {
            return LEGACY_BOOT_LAUNCHER;
        }
        throw new PackageException(
                "Spring Boot loader classes were found, but JarLauncher is missing. Expected "
                        + BOOT_LAUNCHER
                        + " or "
                        + LEGACY_BOOT_LAUNCHER
                        + ".");
    }

    private static byte[] springBootManifest(String startClass, SpringBootLoader loader) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, loader.launcherClass());
        attributes.put(new Attributes.Name("Start-Class"), startClass);
        attributes.put(new Attributes.Name("Spring-Boot-Version"), loader.jar().version());
        attributes.put(new Attributes.Name("Spring-Boot-Classes"), BOOT_CLASSES_PREFIX);
        attributes.put(new Attributes.Name("Spring-Boot-Lib"), BOOT_LIB_PREFIX);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        manifest.write(output);
        return output.toByteArray();
    }

    private static String classEntryName(String className) {
        return className.replace('.', '/') + ".class";
    }

    private static String nestedJarName(RuntimeJar runtimeJar) {
        Path fileName = runtimeJar.jarPath().getFileName();
        if (fileName != null && !fileName.toString().isBlank()) {
            return fileName.toString();
        }
        return runtimeJar.packageId().toString().replace(':', '-') + "-" + runtimeJar.version() + ".jar";
    }

    private static byte[] readRuntimeJar(RuntimeJar runtimeJar) throws IOException {
        if (!Files.isRegularFile(runtimeJar.jarPath())) {
            throw new PackageException(
                    "Runtime dependency jar for "
                            + runtimeJar.packageId()
                            + " is missing at "
                            + runtimeJar.jarPath()
                            + ". Run `zolt resolve` to refresh the artifact cache and retry.");
        }
        return Files.readAllBytes(runtimeJar.jarPath());
    }

    private static Path runtimeClasspathPath(Path jarPath) {
        String fileName = jarPath.getFileName().toString();
        if (fileName.endsWith(".jar")) {
            return jarPath.resolveSibling(fileName.substring(0, fileName.length() - 4) + ".runtime-classpath");
        }
        return jarPath.resolveSibling(fileName + ".runtime-classpath");
    }

    private static List<Path> compiledFiles(Path outputDirectory) throws IOException {
        try (var stream = Files.walk(outputDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> entryName(outputDirectory, path)))
                    .toList();
        }
    }

    private static void writeEntry(JarOutputStream output, String name, byte[] content) throws IOException {
        try {
            JarEntry entry = new JarEntry(name);
            entry.setTime(DETERMINISTIC_ENTRY_TIME);
            output.putNextEntry(entry);
            output.write(content);
            output.closeEntry();
        } catch (ZipException exception) {
            throw new PackageException(
                    "Duplicate jar entry `"
                            + name
                            + "`. Remove or rename the duplicate resource and try packaging again.",
                    exception);
        }
    }

    private static void writeStoredEntry(JarOutputStream output, String name, byte[] content) throws IOException {
        try {
            JarEntry entry = new JarEntry(name);
            entry.setTime(DETERMINISTIC_ENTRY_TIME);
            entry.setMethod(JarEntry.STORED);
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            CRC32 crc = new CRC32();
            crc.update(content);
            entry.setCrc(crc.getValue());
            output.putNextEntry(entry);
            output.write(content);
            output.closeEntry();
        } catch (ZipException exception) {
            throw new PackageException(
                    "Duplicate jar entry `"
                            + name
                            + "`. Remove or rename the duplicate dependency and try packaging again.",
                    exception);
        }
    }

    private static String entryName(Path outputDirectory, Path file) {
        return outputDirectory.relativize(file).normalize().toString().replace('\\', '/');
    }

    private record RuntimeJar(PackageId packageId, String version, Path jarPath) {
    }

    private record SpringBootLoader(RuntimeJar jar, String launcherClass, Map<String, byte[]> entries) {
    }
}
