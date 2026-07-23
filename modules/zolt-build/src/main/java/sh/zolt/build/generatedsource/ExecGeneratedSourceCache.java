package sh.zolt.build.generatedsource;

import static sh.zolt.build.generatedsource.GeneratedSourceHashes.fileHash;
import static sh.zolt.build.generatedsource.GeneratedSourceHashes.relative;
import static sh.zolt.build.generatedsource.GeneratedSourceHashes.sha256;

import sh.zolt.build.BuildException;
import sh.zolt.project.ExecGenerationSettings;
import sh.zolt.project.GeneratedSourceStep;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;

/**
 * Content-fingerprint producer skip-gate for exec steps. The fingerprint hashes tool jar bytes, argv,
 * the expanded input content, env names + configured literal values, and produces/into/cwd. Sidecar
 * files live OUTSIDE the declared output directory so the consumer fence hashes only tool-produced
 * bytes and the resources lane never copies build metadata into a package.
 */
final class ExecGeneratedSourceCache {
    private static final String FINGERPRINT_VERSION = "1";

    private final Path metadataDirectory;

    ExecGeneratedSourceCache(Path metadataDirectory) {
        this.metadataDirectory = metadataDirectory;
    }

    GenerationCacheState state(
            Path projectRoot,
            Path output,
            List<Path> toolClasspath,
            String scope,
            GeneratedSourceStep step) {
        String base = "exec-" + scope + "-" + step.id();
        return new GenerationCacheState(
                metadataDirectory.resolve(base + ".fingerprint"),
                metadataDirectory.resolve(base + ".log"),
                fingerprint(projectRoot, toolClasspath, scope, step));
    }

    boolean isCurrent(Path output, GenerationCacheState state) {
        return Files.isDirectory(output)
                && Files.isRegularFile(state.fingerprint())
                && readFingerprint(state.fingerprint()).equals(state.fingerprintSha256());
    }

    void writeFingerprint(GenerationCacheState state) {
        try {
            Files.createDirectories(state.fingerprint().getParent());
            Files.writeString(state.fingerprint(), state.fingerprintSha256(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not write exec generation fingerprint at " + state.fingerprint()
                            + ". Check filesystem permissions.",
                    exception);
        }
    }

    void writeLog(GenerationCacheState state, String log) {
        try {
            Files.createDirectories(state.log().getParent());
            Files.writeString(state.log(), log, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not write exec generation log at " + state.log() + ". Check filesystem permissions.",
                    exception);
        }
    }

    private static String fingerprint(
            Path projectRoot,
            List<Path> toolClasspath,
            String scope,
            GeneratedSourceStep step) {
        ExecGenerationSettings exec = step.exec();
        StringBuilder content = new StringBuilder();
        content.append("version=").append(FINGERPRINT_VERSION).append('\n');
        content.append("scope=").append(scope).append('\n');
        content.append("id=").append(step.id()).append('\n');
        content.append("tool=").append(exec.toolName()).append('\n');
        content.append("runner=").append(exec.tool().runner()).append('\n');
        content.append("mainClass=").append(exec.tool().mainClass()).append('\n');
        content.append("produces=").append(exec.produces() == null ? "" : exec.produces().configValue()).append('\n');
        content.append("into=").append(exec.into().orElse("")).append('\n');
        content.append("cache=").append(exec.cache()).append('\n');
        content.append("cwd=").append(relative(projectRoot, projectRoot)).append('\n');
        content.append("[args]\n");
        exec.args().forEach(argument -> content.append(argument).append('\n'));
        content.append("[env]\n");
        new TreeMap<>(exec.env()).forEach((name, value) -> content.append(name).append('=').append(value).append('\n'));
        content.append("[coordinates]\n");
        exec.tool().coordinates().forEach(coordinate -> content
                .append(coordinate.coordinate()).append(':').append(coordinate.version().orElse("")).append('\n'));
        content.append("[toolClasspath]\n");
        toolClasspath.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .forEach(path -> content.append(relative(projectRoot, path)).append('|').append(fileHash(path)).append('\n'));
        content.append("[inputs]\n");
        step.inputs().stream()
                .flatMap(input -> ExecInputExpander.expand(projectRoot, input).stream())
                .distinct()
                .sorted()
                .forEach(path -> content.append(relative(projectRoot, path)).append('|').append(fileHash(path)).append('\n'));
        return sha256(content.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String readFingerprint(Path fingerprint) {
        try {
            return Files.readString(fingerprint);
        } catch (IOException exception) {
            return "";
        }
    }

    record GenerationCacheState(Path fingerprint, Path log, String fingerprintSha256) {
    }
}
