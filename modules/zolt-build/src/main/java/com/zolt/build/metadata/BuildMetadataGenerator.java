package com.zolt.build.metadata;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class BuildMetadataGenerator {
    private static final Instant REPRODUCIBLE_BUILD_TIME = Instant.EPOCH;
    private static final Path BUILD_INFO_PATH = Path.of("META-INF/build-info.properties");
    private static final Path GIT_PROPERTIES_PATH = Path.of("git.properties");

    private final Clock clock;
    private final GitMetadataReader gitMetadataReader;

    public BuildMetadataGenerator() {
        this(Clock.systemUTC(), new GitMetadataReader());
    }

    BuildMetadataGenerator(Clock clock, GitMetadataReader gitMetadataReader) {
        this.clock = clock;
        this.gitMetadataReader = gitMetadataReader;
    }

    public BuildMetadataResult generate(Path projectDirectory, ProjectConfig config, Path outputDirectory) {
        BuildMetadataSettings settings = config.build().metadata();
        if (!settings.buildInfo() && !settings.git()) {
            return new BuildMetadataResult(List.of());
        }

        Optional<GitMetadata> gitMetadata = settings.git()
                ? gitMetadataReader.read(projectDirectory, outputDirectory)
                : Optional.empty();
        List<Path> generated = new ArrayList<>();
        if (settings.buildInfo()) {
            generated.add(writeProperties(
                    outputDirectory.resolve(BUILD_INFO_PATH),
                    buildInfoProperties(config, settings)));
        }
        if (settings.git()) {
            gitMetadata.ifPresent(metadata -> generated.add(writeProperties(
                    outputDirectory.resolve(GIT_PROPERTIES_PATH),
                    gitProperties(metadata))));
        }
        return new BuildMetadataResult(generated);
    }

    private Map<String, String> buildInfoProperties(ProjectConfig config, BuildMetadataSettings settings) {
        Instant buildTime = settings.reproducible() ? REPRODUCIBLE_BUILD_TIME : clock.instant();
        Map<String, String> values = new TreeMap<>();
        values.put("build.artifact", config.project().name());
        values.put("build.group", config.project().group());
        values.put("build.name", config.project().name());
        values.put("build.time", DateTimeFormatter.ISO_INSTANT.format(buildTime));
        values.put("build.version", config.project().version());
        return values;
    }

    private static Map<String, String> gitProperties(GitMetadata metadata) {
        Map<String, String> values = new TreeMap<>();
        values.put("git.branch", metadata.branch());
        values.put("git.commit.id", metadata.commitId());
        values.put("git.commit.id.abbrev", metadata.abbreviatedCommitId());
        values.put("git.dirty", Boolean.toString(metadata.dirty()));
        return values;
    }

    private static Path writeProperties(Path path, Map<String, String> values) {
        try {
            Files.createDirectories(path.getParent());
            StringBuilder content = new StringBuilder();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                content.append(entry.getKey())
                        .append('=')
                        .append(escape(entry.getValue()))
                        .append('\n');
            }
            Files.writeString(path, content.toString(), StandardCharsets.UTF_8);
            return path;
        } catch (IOException exception) {
            throw new BuildMetadataException(
                    "Could not write build metadata at "
                            + path
                            + ". Check that the build output directory is writable.",
                    exception);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    record GitMetadata(
            String branch,
            String commitId,
            String abbreviatedCommitId,
            boolean dirty) {
    }

    static final class GitMetadataReader {
        Optional<GitMetadata> read(Path projectDirectory, Path outputDirectory) {
            Optional<String> insideWorkTree = git(projectDirectory, "rev-parse", "--is-inside-work-tree");
            if (insideWorkTree.isEmpty() || !"true".equals(insideWorkTree.orElseThrow())) {
                return Optional.empty();
            }
            Optional<String> commitId = git(projectDirectory, "rev-parse", "HEAD");
            Optional<String> abbreviatedCommitId = git(projectDirectory, "rev-parse", "--short=12", "HEAD");
            if (commitId.isEmpty() || abbreviatedCommitId.isEmpty()) {
                return Optional.empty();
            }
            String branch = git(projectDirectory, "branch", "--show-current")
                    .filter(value -> !value.isBlank())
                    .orElse("HEAD");
            boolean dirty = git(projectDirectory, statusArguments(projectDirectory, outputDirectory))
                    .map(status -> !status.isBlank())
                    .orElse(false);
            return Optional.of(new GitMetadata(
                    branch,
                    commitId.orElseThrow(),
                    abbreviatedCommitId.orElseThrow(),
                    dirty));
        }

        private static String[] statusArguments(Path projectDirectory, Path outputDirectory) {
            Optional<String> outputPath = projectRelativePath(projectDirectory, outputDirectory);
            if (outputPath.isEmpty()) {
                return new String[] {"status", "--porcelain"};
            }
            return new String[] {"status", "--porcelain", "--", ".", ":(exclude)" + outputPath.orElseThrow()};
        }

        private static Optional<String> projectRelativePath(Path projectDirectory, Path path) {
            Path projectRoot = projectDirectory.toAbsolutePath().normalize();
            Path normalizedPath = path.toAbsolutePath().normalize();
            if (!normalizedPath.startsWith(projectRoot) || normalizedPath.equals(projectRoot)) {
                return Optional.empty();
            }
            return Optional.of(projectRoot.relativize(normalizedPath).toString().replace('\\', '/'));
        }

        private static Optional<String> git(Path projectDirectory, String... arguments) {
            ProcessResult result = run(projectDirectory, arguments);
            if (result.exitCode() != 0) {
                return Optional.empty();
            }
            return Optional.of(result.output().trim());
        }

        private static ProcessResult run(Path projectDirectory, String... arguments) {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.add("-C");
            command.add(projectDirectory.toString());
            command.addAll(List.of(arguments));
            try {
                Process process = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exitCode = process.waitFor();
                return new ProcessResult(exitCode, output);
            } catch (IOException exception) {
                return new ProcessResult(-1, "");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new ProcessResult(-1, "");
            }
        }

        private record ProcessResult(int exitCode, String output) {
        }
    }
}
