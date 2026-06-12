package com.zolt.build;

import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceMissingTokenPolicy;
import com.zolt.project.ResourceTokenSettings;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ResourceCopier {
    private static final Set<String> OUTPUT_DIRECTORY_NAMES = Set.of("target", "build");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("@([A-Za-z0-9_.-]+)@");

    public ResourceCopyResult copyMainResources(Path projectDirectory, BuildSettings settings) {
        return copyMainResources(projectDirectory, settings, Optional.empty());
    }

    public ResourceCopyResult copyMainResources(Path projectDirectory, ProjectConfig config) {
        return copyMainResources(projectDirectory, config.build(), Optional.of(config.project()));
    }

    private ResourceCopyResult copyMainResources(
            Path projectDirectory,
            BuildSettings settings,
            Optional<ProjectMetadata> project) {
        return copyResources(
                settings.resourceRoots(),
                outputDirectory(projectDirectory, "[build].output", settings.output()),
                projectDirectory,
                settings,
                project,
                "[resources].main",
                false);
    }

    public ResourceCopyResult copyTestResources(Path projectDirectory, BuildSettings settings) {
        return copyTestResources(projectDirectory, settings, Optional.empty());
    }

    public ResourceCopyResult copyTestResources(Path projectDirectory, ProjectConfig config) {
        return copyTestResources(projectDirectory, config.build(), Optional.of(config.project()));
    }

    private ResourceCopyResult copyTestResources(
            Path projectDirectory,
            BuildSettings settings,
            Optional<ProjectMetadata> project) {
        return copyResources(
                settings.testResourceRoots(),
                outputDirectory(projectDirectory, "[build].testOutput", settings.testOutput()),
                projectDirectory,
                settings,
                project,
                "[resources].test",
                true);
    }

    private static ResourceCopyResult copyResources(
            List<String> configuredRoots,
            Path outputDirectory,
            Path projectDirectory,
            BuildSettings settings,
            Optional<ProjectMetadata> project,
            String resourceRootKey,
            boolean testResources) {
        Path projectRoot = ProjectPaths.root(projectDirectory);
        Path mainOutput = outputPath(projectRoot, "[build].output", settings.output());
        Path testOutput = outputPath(projectRoot, "[build].testOutput", settings.testOutput());
        ResourceFilteringSettings filtering = settings.resourceFiltering();
        boolean filteringEnabled = filtering.enabled() && (!testResources || filtering.testEnabled());
        Map<String, String> tokenValues = filteringEnabled
                ? tokenValues(filtering.tokens(), project)
                : Map.of();
        List<Path> copiedResources = new ArrayList<>();
        List<Path> skippedResources = new ArrayList<>();
        Set<Path> targetRelativePaths = new HashSet<>();
        for (String configuredRoot : configuredRoots) {
            Path resourceRoot = resourceRoot(projectRoot, resourceRootKey, configuredRoot);
            if (!Files.isDirectory(resourceRoot)) {
                continue;
            }

            try (Stream<Path> paths = Files.walk(resourceRoot)) {
                List<Path> resources = paths
                        .filter(path -> ProjectPaths.isRegularFileInsideProject(projectRoot, resourceRootKey, path))
                        .map(Path::normalize)
                        .filter(path -> !path.getFileName().toString().endsWith(".java"))
                        .filter(path -> !path.startsWith(mainOutput))
                        .filter(path -> !path.startsWith(testOutput))
                        .filter(path -> !startsWithOutputDirectorySegment(resourceRoot.relativize(path)))
                        .sorted()
                        .toList();

                for (Path resource : resources) {
                    Path relativePath = resourceRoot.relativize(resource).normalize();
                    if (!targetRelativePaths.add(relativePath)) {
                        throw new ResourceCopyException(
                                "Duplicate resource path `"
                                        + relativePath.toString().replace('\\', '/')
                                        + "` from configured resource roots. Remove one copy or choose a distinct output path.");
                    }
                    Path target = outputDirectory.resolve(relativePath).normalize();
                    Files.createDirectories(target.getParent());
                    Optional<byte[]> filtered = filteringEnabled && matchesFilter(relativePath, filtering.includes())
                            ? Optional.of(filteredBytes(resource, relativePath, filtering, tokenValues))
                            : Optional.empty();
                    if (isCurrent(resource, target, filtered)) {
                        skippedResources.add(resource);
                    } else if (filtered.isPresent()) {
                        Files.write(target, filtered.orElseThrow());
                        copiedResources.add(resource);
                    } else {
                        Files.copy(resource, target, StandardCopyOption.REPLACE_EXISTING);
                        copiedResources.add(resource);
                    }
                }
            } catch (IOException exception) {
                throw new ResourceCopyException(
                        "Could not copy resources from "
                                + resourceRoot
                                + " to "
                                + outputDirectory
                                + ". Check that the project directories are readable and writable.",
                    exception);
            } catch (ProjectPathException exception) {
                throw new ResourceCopyException(exception.getMessage(), exception);
            }
        }
        return new ResourceCopyResult(copiedResources, skippedResources);
    }

    private static boolean isCurrent(Path source, Path target, Optional<byte[]> filteredContent) throws IOException {
        if (filteredContent.isPresent()) {
            byte[] content = filteredContent.orElseThrow();
            return Files.isRegularFile(target)
                    && Files.size(target) == content.length
                    && java.util.Arrays.equals(Files.readAllBytes(target), content);
        }
        return Files.isRegularFile(target)
                && Files.size(source) == Files.size(target)
                && Files.mismatch(source, target) == -1L;
    }

    private static boolean matchesFilter(Path relativePath, List<String> includes) {
        if (includes.isEmpty()) {
            return false;
        }
        String normalized = relativePath.toString().replace('\\', '/');
        return includes.stream().anyMatch(pattern -> globMatches(pattern, normalized));
    }

    private static boolean globMatches(String pattern, String normalizedPath) {
        try {
            if (java.nio.file.FileSystems.getDefault()
                    .getPathMatcher("glob:" + pattern)
                    .matches(Path.of(normalizedPath))) {
                return true;
            }
            return pattern.startsWith("**/")
                    && java.nio.file.FileSystems.getDefault()
                            .getPathMatcher("glob:" + pattern.substring(3))
                            .matches(Path.of(normalizedPath));
        } catch (IllegalArgumentException exception) {
            throw new ResourceCopyException(
                    "Invalid resource filtering include glob `"
                            + pattern
                            + "`. Use resource-relative glob patterns such as **/*.properties.",
                    exception);
        }
    }

    private static byte[] filteredBytes(
            Path resource,
            Path relativePath,
            ResourceFilteringSettings filtering,
            Map<String, String> tokenValues) throws IOException {
        byte[] bytes = Files.readAllBytes(resource);
        if (containsNul(bytes)) {
            throw new ResourceCopyException(
                    "Resource `"
                            + relativePath.toString().replace('\\', '/')
                            + "` was selected for filtering but appears to be binary. Remove it from [resources.filtering].includes or use a text resource.");
        }
        String content = decodeUtf8(resource, relativePath, bytes);
        Matcher matcher = TOKEN_PATTERN.matcher(content);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String value = tokenValues.get(token);
            if (value == null) {
                if (filtering.missing() == ResourceMissingTokenPolicy.FAIL) {
                    throw new ResourceCopyException(
                            "Resource `"
                                    + relativePath.toString().replace('\\', '/')
                                    + "` contains token @"
                                    + token
                                    + "@ but [resources.tokens]."
                                    + token
                                    + " is not defined. Add the token or set [resources.filtering].missing = \"keep\".");
                }
                matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(output, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(output);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String decodeUtf8(Path resource, Path relativePath, byte[] bytes) {
        try {
            java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException exception) {
            throw new ResourceCopyException(
                    "Resource `"
                            + relativePath.toString().replace('\\', '/')
                            + "` was selected for filtering but is not valid UTF-8 text. Remove it from [resources.filtering].includes or convert it to UTF-8.",
                    exception);
        }
    }

    private static boolean containsNul(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> tokenValues(
            Map<String, ResourceTokenSettings> tokens,
            Optional<ProjectMetadata> project) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, ResourceTokenSettings> entry : tokens.entrySet()) {
            ResourceTokenSettings token = entry.getValue();
            String value = token.value()
                    .or(() -> token.env().map(ResourceCopier::environmentToken))
                    .or(() -> token.project().map(projectField -> projectToken(project, projectField)))
                    .orElseThrow();
            values.put(entry.getKey(), value);
        }
        return values;
    }

    private static String environmentToken(String variable) {
        String value = System.getenv(variable);
        if (value == null) {
            throw new ResourceCopyException(
                    "Resource filtering token reads environment variable `"
                            + variable
                            + "`, but it is not set. Export the variable or change [resources.tokens].");
        }
        return value;
    }

    private static String projectToken(Optional<ProjectMetadata> project, String field) {
        ProjectMetadata metadata = project.orElseThrow(() -> new ResourceCopyException(
                "Resource filtering token references project field `"
                        + field
                        + "`, but project metadata is unavailable. Use zolt build/test/package so filtering can read [project]."));
        return switch (field) {
            case "name" -> metadata.name();
            case "version" -> metadata.version();
            case "group" -> metadata.group();
            case "java" -> metadata.java();
            case "main" -> metadata.main().orElse("");
            default -> throw new ResourceCopyException(
                    "Unsupported resource filtering project field `"
                            + field
                            + "`. Supported project fields are: name, version, group, java, main.");
        };
    }

    private static Path outputDirectory(Path projectDirectory, String key, String configuredPath) {
        return outputPath(ProjectPaths.root(projectDirectory), key, configuredPath);
    }

    private static Path outputPath(Path projectRoot, String key, String configuredPath) {
        try {
            return ProjectPaths.output(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new ResourceCopyException(exception.getMessage(), exception);
        }
    }

    private static Path resourceRoot(Path projectRoot, String key, String configuredRoot) {
        try {
            return ProjectPaths.existingRoot(projectRoot, key, configuredRoot);
        } catch (ProjectPathException exception) {
            throw new ResourceCopyException(exception.getMessage(), exception);
        }
    }

    private static boolean startsWithOutputDirectorySegment(Path relativePath) {
        if (relativePath.getNameCount() == 0) {
            return false;
        }
        return OUTPUT_DIRECTORY_NAMES.contains(relativePath.getName(0).toString());
    }
}
