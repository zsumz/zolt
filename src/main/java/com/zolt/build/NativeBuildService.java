package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.NativeSettings;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class NativeBuildService {
    private static final List<String> SERIOUS_WARNING_TERMS = List.of("warning", "unsupported", "error");

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
        String mainClass = config.project().main().orElseThrow(() -> new NativeImageException(
                "Native Image main class is missing. Add [project].main to zolt.toml."));
        PackageResult packageResult = packageService.packageJar(projectDirectory, config, cacheRoot);

        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        NativeSettings nativeSettings = config.nativeSettings().withDefaultImageName(config.project().name());
        Path outputDirectory = projectDirectory.resolve(nativeSettings.output());
        NativeImageResult nativeImageResult = nativeImageRunner.build(new NativeImageRequest(
                nativeImageExecutable,
                packageResult.jarPath(),
                classpaths.runtime().entries(),
                mainClass,
                outputDirectory.resolve(nativeSettings.imageName()),
                outputDirectory.resolve("native-image.log"),
                nativeSettings.args()));
        reportSeriousWarnings(nativeImageResult);
        return new NativeBuildResult(packageResult, nativeImageResult);
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
