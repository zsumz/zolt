package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.classpath.LockfileClasspathPackageConverter;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.NativeSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPaths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class NativeBuildService {
    private static final List<String> SERIOUS_WARNING_TERMS = List.of("warning", "unsupported", "error");
    private static final String SPRING_BOOT_GROUP = "org.springframework.boot";
    private static final String MICRONAUT_GROUP = "io.micronaut";
    private static final String QUARKUS_GROUP = "io.quarkus";

    private final PackageService packageService;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final NativeImageRunner nativeImageRunner;

    public NativeBuildService() {
        this(
                new PackageService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new NativeImageRunner());
    }

    NativeBuildService(
            PackageService packageService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            NativeImageRunner nativeImageRunner) {
        this.packageService = packageService;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.nativeImageRunner = nativeImageRunner;
    }

    public NativeBuildResult buildNative(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            Path nativeImageExecutable) {
        rejectUnsupportedFrameworkNative(config);
        nativeMainClass(config);
        PackageResult packageResult = packageService.packageJar(
                projectDirectory,
                config.withPackageSettings(PackageSettings.defaults()),
                cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        ClasspathSet classpaths = classpathBuilder.build(LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot).stream()
                .filter(dependency -> dependency.scope().packagedByDefault())
                .toList());
        return buildNativeImage(
                projectDirectory,
                config,
                packageResult,
                classpaths.runtime().entries(),
                nativeImageExecutable);
    }

    public NativeBuildResult buildNativeImage(
            Path projectDirectory,
            ProjectConfig config,
            PackageResult packageResult,
            List<Path> runtimeClasspath,
            Path nativeImageExecutable) {
        rejectUnsupportedFrameworkNative(config);
        String mainClass = nativeMainClass(config);
        NativeSettings nativeSettings = config.nativeSettings().withDefaultImageName(config.project().name());
        Path projectRoot = ProjectPaths.root(projectDirectory);
        Path outputDirectory = ProjectPaths.output(projectRoot, "[native].output", nativeSettings.output());
        String imageName = ProjectPaths.filenameComponent("[native].imageName", nativeSettings.imageName());
        Optional<Path> springBootAotEvidencePath = Optional.empty();
        List<Path> springBootAotClasspath = config.frameworkSettings().springBoot().nativeEnabled()
                ? new SpringBootAotNativeInputs(projectRoot, config.build().outputRoot()).classpathEntries()
                : List.of();
        if (config.frameworkSettings().springBoot().nativeEnabled()) {
            springBootAotEvidencePath = Optional.of(new SpringBootAotOutputEvidenceService().write(
                    projectRoot,
                    config.build().outputRoot(),
                    outputDirectory.resolve("spring-aot-evidence.json")));
        }
        List<Path> nativeRuntimeClasspath = new ArrayList<>(runtimeClasspath == null ? List.of() : runtimeClasspath);
        nativeRuntimeClasspath.addAll(0, springBootAotClasspath);
        NativeImageResult nativeImageResult = nativeImageRunner.build(new NativeImageRequest(
                nativeImageExecutable,
                packageResult.jarPath(),
                nativeRuntimeClasspath,
                mainClass,
                outputDirectory.resolve(imageName),
                outputDirectory.resolve("native-image.log"),
                nativeSettings.args()));
        reportSeriousWarnings(nativeImageResult);
        return new NativeBuildResult(packageResult, nativeImageResult, springBootAotEvidencePath);
    }

    private static String nativeMainClass(ProjectConfig config) {
        return config.project().main().orElseThrow(() -> new NativeImageException(
                "Native Image main class is missing. Add [project].main to zolt.toml."));
    }

    private static void rejectUnsupportedFrameworkNative(ProjectConfig config) {
        if (!config.frameworkSettings().springBoot().nativeEnabled() && springBootProject(config)) {
            throw new NativeImageException(
                    "Spring Boot native images require `[framework.springBoot.native] enabled = true`. "
                            + "Zolt supports Spring Boot JVM build, test, run, and executable packaging, "
                            + "and supports an explicit Zolt-owned Spring Boot AOT/native canary path when that flag is enabled. "
                            + "Use `zolt package --mode spring-boot` or `zolt run` for JVM apps, or enable the typed Spring Boot native path.");
        }
        if (micronautProject(config)) {
            throw new NativeImageException(
                    "Micronaut native images are not supported by Zolt yet. "
                            + "Zolt supports basic Micronaut JVM build/test flows through Java annotation processors, "
                            + "but does not run Micronaut AOT or framework-native processing in the public beta. "
                            + "Use `zolt build`, `zolt test`, or `zolt package --mode thin` for the current beta path.");
        }
        if (quarkusProject(config)) {
            throw new NativeImageException(
                    "Quarkus native images are not supported by Zolt yet. "
                            + "Zolt supports the experimental Quarkus JVM build/test/package path, "
                            + "but does not run Quarkus native augmentation, dev mode, or advanced native modes in the public beta. "
                            + "Use `zolt package --mode quarkus` or `zolt run` for the current beta path.");
        }
    }

    private static boolean springBootProject(ProjectConfig config) {
        PackageMode packageMode = config.packageSettings().mode();
        if (packageMode == PackageMode.SPRING_BOOT || packageMode == PackageMode.SPRING_BOOT_WAR) {
            return true;
        }
        if (containsSpringBootCoordinate(config.platforms().keySet())) {
            return true;
        }
        return containsSpringBootCoordinate(config.apiDependencies().keySet())
                || containsSpringBootCoordinate(config.managedApiDependencies())
                || containsSpringBootCoordinate(config.dependencies().keySet())
                || containsSpringBootCoordinate(config.managedDependencies())
                || containsSpringBootCoordinate(config.runtimeDependencies().keySet())
                || containsSpringBootCoordinate(config.managedRuntimeDependencies())
                || containsSpringBootCoordinate(config.providedDependencies().keySet())
                || containsSpringBootCoordinate(config.managedProvidedDependencies())
                || containsSpringBootCoordinate(config.devDependencies().keySet())
                || containsSpringBootCoordinate(config.managedDevDependencies())
                || containsSpringBootCoordinate(config.testDependencies().keySet())
                || containsSpringBootCoordinate(config.managedTestDependencies())
                || containsSpringBootCoordinate(config.annotationProcessors().keySet())
                || containsSpringBootCoordinate(config.managedAnnotationProcessors())
                || containsSpringBootCoordinate(config.testAnnotationProcessors().keySet())
                || containsSpringBootCoordinate(config.managedTestAnnotationProcessors());
    }

    private static boolean micronautProject(ProjectConfig config) {
        return containsMicronautCoordinate(config.platforms().keySet())
                || containsMicronautCoordinate(config.apiDependencies().keySet())
                || containsMicronautCoordinate(config.managedApiDependencies())
                || containsMicronautCoordinate(config.dependencies().keySet())
                || containsMicronautCoordinate(config.managedDependencies())
                || containsMicronautCoordinate(config.runtimeDependencies().keySet())
                || containsMicronautCoordinate(config.managedRuntimeDependencies())
                || containsMicronautCoordinate(config.providedDependencies().keySet())
                || containsMicronautCoordinate(config.managedProvidedDependencies())
                || containsMicronautCoordinate(config.devDependencies().keySet())
                || containsMicronautCoordinate(config.managedDevDependencies())
                || containsMicronautCoordinate(config.testDependencies().keySet())
                || containsMicronautCoordinate(config.managedTestDependencies())
                || containsMicronautCoordinate(config.annotationProcessors().keySet())
                || containsMicronautCoordinate(config.managedAnnotationProcessors())
                || containsMicronautCoordinate(config.testAnnotationProcessors().keySet())
                || containsMicronautCoordinate(config.managedTestAnnotationProcessors());
    }

    private static boolean quarkusProject(ProjectConfig config) {
        return config.packageSettings().mode() == PackageMode.QUARKUS || config.frameworkSettings().quarkus().enabled();
    }

    private static boolean containsSpringBootCoordinate(Iterable<String> coordinates) {
        return containsGroupCoordinate(coordinates, SPRING_BOOT_GROUP);
    }

    private static boolean containsMicronautCoordinate(Iterable<String> coordinates) {
        return containsGroupCoordinate(coordinates, MICRONAUT_GROUP);
    }

    private static boolean containsQuarkusCoordinate(Iterable<String> coordinates) {
        return containsGroupCoordinate(coordinates, QUARKUS_GROUP);
    }

    private static boolean containsGroupCoordinate(Iterable<String> coordinates, String group) {
        for (String coordinate : coordinates) {
            if (coordinate != null && coordinate.startsWith(group + ":")) {
                return true;
            }
        }
        return false;
    }

    private static void reportSeriousWarnings(NativeImageResult result) {
        List<String> matches = result.output().lines()
                .filter(NativeBuildService::containsSeriousWarningTerm)
                .toList();
        if (!matches.isEmpty()) {
            throw new NativeImageException(
                    "Native Image output contains serious warning terms. Review "
                            + result.logFile()
                            + " and fix or document these lines:\n"
                            + String.join("\n", matches));
        }
    }

    private static boolean containsSeriousWarningTerm(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return SERIOUS_WARNING_TERMS.stream().anyMatch(lower::contains);
    }
}
