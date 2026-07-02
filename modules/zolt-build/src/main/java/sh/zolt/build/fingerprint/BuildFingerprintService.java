package sh.zolt.build.fingerprint;

import sh.zolt.build.BuildException;
import sh.zolt.build.discovery.SourceDiscoveryResult;
import sh.zolt.classpath.Classpath;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BuildFingerprintService {
    private static final String MAIN_FILE_NAME = ".zolt-build-main.fingerprint";
    private static final String TEST_FILE_NAME = ".zolt-build-test.fingerprint";
    private final BuildFingerprintContent content = new BuildFingerprintContent();
    private final BuildFingerprintExpectedClasses expectedClasses = new BuildFingerprintExpectedClasses();
    private final BuildFingerprintStateStore stateStore = new BuildFingerprintStateStore();

    public boolean isMainCompileCurrent(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        return isCompileCurrent(
                projectDirectory,
                config,
                lockfilePath,
                mainSourceRoots(config.build()),
                config.build().resourceRoots(),
                "[resources].main",
                sources.mainSources(),
                config.build().generatedMainSources(),
                classpaths.compile(),
                classpaths.processor(),
                outputDirectory,
                config.build().output(),
                generatedSourcesDirectory,
                MAIN_FILE_NAME);
    }

    public void writeMainCompileFingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            SourceDiscoveryResult sources,
            ClasspathSet classpaths,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        writeCompileFingerprint(
                projectDirectory,
                config,
                lockfilePath,
                mainSourceRoots(config.build()),
                config.build().resourceRoots(),
                "[resources].main",
                sources.mainSources(),
                config.build().generatedMainSources(),
                classpaths.compile(),
                classpaths.processor(),
                outputDirectory,
                config.build().output(),
                generatedSourcesDirectory,
                MAIN_FILE_NAME);
    }

    public boolean isTestCompileCurrent(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            SourceDiscoveryResult sources,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        return isCompileCurrent(
                projectDirectory,
                config,
                lockfilePath,
                testSourceRoots(config.build()),
                config.build().testResourceRoots(),
                "[resources].test",
                sources.allTestSources(),
                config.build().generatedTestSources(),
                compileClasspath,
                processorClasspath,
                outputDirectory,
                config.build().testOutput(),
                generatedSourcesDirectory,
                TEST_FILE_NAME);
    }

    public void writeTestCompileFingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            SourceDiscoveryResult sources,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            Path generatedSourcesDirectory) {
        writeCompileFingerprint(
                projectDirectory,
                config,
                lockfilePath,
                testSourceRoots(config.build()),
                config.build().testResourceRoots(),
                "[resources].test",
                sources.allTestSources(),
                config.build().generatedTestSources(),
                compileClasspath,
                processorClasspath,
                outputDirectory,
                config.build().testOutput(),
                generatedSourcesDirectory,
                TEST_FILE_NAME);
    }

    private static List<String> testSourceRoots(BuildSettings settings) {
        List<String> roots = new ArrayList<>();
        roots.addAll(settings.testSources());
        roots.addAll(settings.generatedTestSources().stream()
                .map(GeneratedSourceStep::output)
                .toList());
        roots.addAll(settings.groovyTestSources());
        return List.copyOf(roots);
    }

    private static List<String> mainSourceRoots(BuildSettings settings) {
        List<String> roots = new ArrayList<>();
        roots.addAll(settings.sourceRoots());
        roots.addAll(settings.generatedMainSources().stream()
                .map(GeneratedSourceStep::output)
                .toList());
        return List.copyOf(roots);
    }

    private boolean isCompileCurrent(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            List<String> sourceRoots,
            List<String> resourceRoots,
            String resourceRootKey,
            List<Path> sources,
            List<GeneratedSourceStep> generatedSteps,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            String outputDirectoryName,
            Path generatedSourcesDirectory,
            String fileName) {
        Path fingerprintPath = stateStore.fingerprintPath(outputDirectory, fileName);
        if (!Files.isRegularFile(fingerprintPath)) {
            return false;
        }
        if (!Files.isDirectory(outputDirectory)) {
            return false;
        }
        if (!expectedClasses.present(
                projectDirectory.toAbsolutePath().normalize(),
                sourceRoots,
                sources,
                outputDirectory)) {
            return false;
        }
        if (!processorClasspath.entries().isEmpty() && !Files.isDirectory(generatedSourcesDirectory)) {
            return false;
        }
        try {
            String existing = Files.readString(fingerprintPath);
            Optional<BuildFingerprintState> state = stateStore.readState(fingerprintPath);
            if (state.isPresent() && state.orElseThrow().matchesFingerprint(existing)) {
                try {
                    return existing.equals(content.fingerprint(
                            projectDirectory,
                            config,
                            lockfilePath,
                            sourceRoots,
                            resourceRoots,
                            resourceRootKey,
                            sources,
                            generatedSteps,
                            compileClasspath,
                            processorClasspath,
                            outputDirectory,
                            outputDirectoryName,
                            generatedSourcesDirectory,
                            state.orElseThrow(),
                            null));
                } catch (BuildFingerprintStateMiss ignored) {
                    // Fall back to the full content-hash path below.
                }
            }
            return existing.equals(content.fingerprint(
                    projectDirectory,
                    config,
                    lockfilePath,
                    sourceRoots,
                    resourceRoots,
                    resourceRootKey,
                    sources,
                    generatedSteps,
                    compileClasspath,
                    processorClasspath,
                    outputDirectory,
                    outputDirectoryName,
                    generatedSourcesDirectory,
                    null,
                    null));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not read build fingerprint at "
                            + fingerprintPath
                            + ". Delete the file or rerun `zolt build` to refresh it.",
                    exception);
        }
    }

    private void writeCompileFingerprint(
            Path projectDirectory,
            ProjectConfig config,
            Path lockfilePath,
            List<String> sourceRoots,
            List<String> resourceRoots,
            String resourceRootKey,
            List<Path> sources,
            List<GeneratedSourceStep> generatedSteps,
            Classpath compileClasspath,
            Classpath processorClasspath,
            Path outputDirectory,
            String outputDirectoryName,
            Path generatedSourcesDirectory,
            String fileName) {
        Path fingerprintPath = stateStore.fingerprintPath(outputDirectory, fileName);
        try {
            Files.createDirectories(fingerprintPath.getParent());
            Map<Path, BuildFingerprintCachedFileHash> state = new LinkedHashMap<>();
            String fingerprint = content.fingerprint(
                    projectDirectory,
                    config,
                    lockfilePath,
                    sourceRoots,
                    resourceRoots,
                    resourceRootKey,
                    sources,
                    generatedSteps,
                    compileClasspath,
                    processorClasspath,
                    outputDirectory,
                    outputDirectoryName,
                    generatedSourcesDirectory,
                    null,
                    state);
            Files.writeString(fingerprintPath, fingerprint, StandardCharsets.UTF_8);
            stateStore.writeState(fingerprintPath, fingerprint, state);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not write build fingerprint at "
                            + fingerprintPath
                            + ". Check that the build output directory is writable.",
                    exception);
        }
    }

}
