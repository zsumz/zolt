package com.zolt.explain.gradle;

import com.zolt.explain.ExplainSignal;
import com.zolt.explain.ExplainSignals;
import com.zolt.explain.MigrationExplainException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class GradleStaticProjectInspector {
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\binclude\\b\\s*(?:\\(([^)]*)\\)|([^\\n]+))");
    private static final Pattern INCLUDE_BUILD_PATTERN = Pattern.compile("\\bincludeBuild\\b\\s*(?:\\(([^)]*)\\)|([^\\n]+))");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("['\"]([^'\"]+)['\"]");
    private final GradleBuildFileParser buildFileParser = new GradleBuildFileParser();
    private final GradleMigrationSignalDetector signalDetector = new GradleMigrationSignalDetector();

    public GradleInspectionResult inspect(Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Optional<Path> settingsFile = firstRegularFile(
                normalizedRoot.resolve("settings.gradle"),
                normalizedRoot.resolve("settings.gradle.kts"));
        Optional<Path> rootBuildFile = buildFile(normalizedRoot);
        if (settingsFile.isEmpty() && rootBuildFile.isEmpty()) {
            throw new MigrationExplainException(
                    "Could not inspect Gradle project. Expected settings.gradle, settings.gradle.kts, build.gradle, or build.gradle.kts at "
                            + normalizedRoot
                            + ". Run zolt explain from a Gradle project root or pass --cwd.");
        }

        List<ExplainSignal> signals = new ArrayList<>();
        Map<String, String> versionCatalog = new LinkedHashMap<>();
        List<GradleVersionCatalogAlias> aliases = new ArrayList<>(parseVersionCatalog(
                normalizedRoot.resolve("gradle/libs.versions.toml"),
                versionCatalog,
                signals));
        List<String> includedProjects = settingsFile
                .map(path -> parseIncludedProjects(read(path)))
                .orElseGet(List::of);
        settingsFile.ifPresent(path -> signals.addAll(settingsSignals(normalizedRoot, path, read(path))));
        if (Files.isDirectory(normalizedRoot.resolve("buildSrc"))) {
            signals.add(ExplainSignals.GRADLE_BUILD_SRC_DETECTED.signal(
                    ".",
                    "Gradle buildSrc is present."));
        }

        List<GradleProjectInspection> projects = new ArrayList<>();
        rootBuildFile.ifPresent(path -> projects.add(inspectProject(
                normalizedRoot,
                normalizedRoot,
                path,
                versionCatalog,
                signals)));
        for (String includedProject : includedProjects) {
            Path projectDirectory = normalizedRoot.resolve(includedProject).normalize();
            Optional<Path> includedBuildFile = buildFile(projectDirectory);
            if (includedBuildFile.isPresent()) {
                projects.add(inspectProject(normalizedRoot, projectDirectory, includedBuildFile.orElseThrow(), versionCatalog, signals));
            } else {
                signals.add(ExplainSignals.GRADLE_PROJECT_MISSING_BUILD_FILE.signal(
                        projectLabel(normalizedRoot, projectDirectory),
                        "Included Gradle project `" + includedProject + "` does not contain build.gradle or build.gradle.kts."));
            }
        }

        projects.sort(Comparator.comparing(project -> project.path().toString()));
        aliases.sort(Comparator.comparing(GradleVersionCatalogAlias::alias));
        return new GradleInspectionResult(
                normalizedRoot,
                settingsFile.map(path -> normalizedRoot.relativize(path).toString()).orElse(""),
                includedProjects.stream().sorted().toList(),
                aliases,
                projects,
                ExplainSignals.sorted(signals));
    }

    private GradleProjectInspection inspectProject(
            Path root,
            Path projectDirectory,
            Path buildFile,
            Map<String, String> versionCatalog,
            List<ExplainSignal> signals) {
        String content = stripComments(read(buildFile));
        Path relativePath = relativePath(root, projectDirectory);
        String project = path(relativePath);
        List<GradlePluginInspection> plugins = buildFileParser.plugins(content);
        List<GradleDependencyInspection> dependencies = buildFileParser.dependencies(content, versionCatalog);
        signals.addAll(signalDetector.signals(project, content, dependencies, plugins));
        return new GradleProjectInspection(
                relativePath,
                relativePath.toString().equals(".") ? projectDirectory.getFileName().toString() : projectDirectory.getFileName().toString(),
                buildFile.getFileName().toString(),
                buildFile.getFileName().toString().endsWith(".kts") ? "kotlin" : "groovy",
                buildFileParser.javaVersion(content),
                plugins,
                buildFileParser.repositories(content),
                dependencies,
                buildFileParser.sourceRoots(content, "main", "src/main/java"),
                buildFileParser.sourceRoots(content, "test", "src/test/java"));
    }

    private static List<String> parseIncludedProjects(String content) {
        List<String> projects = new ArrayList<>();
        Matcher matcher = INCLUDE_PATTERN.matcher(stripComments(content));
        while (matcher.find()) {
            String arguments = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            for (String value : quotedValues(arguments)) {
                String path = value.replaceFirst("^:+", "").replace(':', '/');
                if (!path.isBlank()) {
                    projects.add(path);
                }
            }
        }
        return projects.stream().distinct().sorted().toList();
    }

    private static List<ExplainSignal> settingsSignals(Path root, Path settingsFile, String content) {
        List<ExplainSignal> signals = new ArrayList<>();
        Matcher matcher = INCLUDE_BUILD_PATTERN.matcher(stripComments(content));
        while (matcher.find()) {
            String arguments = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            for (String value : quotedValues(arguments)) {
                signals.add(ExplainSignals.GRADLE_INCLUDED_BUILD_DETECTED.signal(
                        ".",
                        "Included Gradle build `" + value + "` is declared in " + root.relativize(settingsFile) + "."));
            }
        }
        return signals;
    }

    private static List<GradleVersionCatalogAlias> parseVersionCatalog(
            Path catalogPath,
            Map<String, String> aliases,
            List<ExplainSignal> signals) {
        if (!Files.isRegularFile(catalogPath)) {
            return List.of();
        }
        TomlParseResult result;
        try {
            result = Toml.parse(catalogPath);
        } catch (IOException exception) {
            throw new MigrationExplainException("Could not read Gradle version catalog for zolt explain: " + catalogPath, exception);
        }
        if (result.hasErrors()) {
            TomlParseError firstError = result.errors().getFirst();
            signals.add(ExplainSignals.GRADLE_VERSION_CATALOG_MALFORMED.signal(
                    ".",
                    "Gradle version catalog could not be parsed near " + firstError.position() + "."));
            return List.of();
        }
        Map<String, String> versions = new LinkedHashMap<>();
        TomlTable versionTable = result.getTable("versions");
        if (versionTable != null) {
            for (String key : versionTable.keySet()) {
                Object value = versionTable.get(key);
                if (value instanceof String stringValue && !stringValue.isBlank()) {
                    versions.put(key, stringValue);
                }
            }
        }
        List<GradleVersionCatalogAlias> parsed = new ArrayList<>();
        TomlTable libraries = result.getTable("libraries");
        if (libraries == null) {
            return List.of();
        }
        for (String key : libraries.keySet()) {
            Optional<String> coordinate = libraryCoordinate(libraries, key, versions);
            if (coordinate.isPresent()) {
                String value = coordinate.orElseThrow();
                aliases.put(key, value);
                aliases.put(key.replace('-', '.'), value);
                parsed.add(new GradleVersionCatalogAlias(key, value));
            }
        }
        return parsed;
    }

    private static Optional<String> libraryCoordinate(TomlTable libraries, String key, Map<String, String> versions) {
        Object raw = libraries.get(key);
        if (raw instanceof String value && !value.isBlank()) {
            return Optional.of(value);
        }
        if (!(raw instanceof TomlTable table)) {
            return Optional.empty();
        }
        String module = nullToEmpty(table.getString("module"));
        if (module.isBlank()) {
            String group = nullToEmpty(table.getString("group"));
            String name = nullToEmpty(table.getString("name"));
            if (!group.isBlank() && !name.isBlank()) {
                module = group + ":" + name;
            }
        }
        if (module.isBlank()) {
            return Optional.empty();
        }
        Object rawVersion = table.get("version");
        String version = rawVersion instanceof String stringVersion ? stringVersion : "";
        if (version.isBlank()) {
            version = nullToEmpty(table.getString("version.ref"));
            if (version.isBlank()) {
                TomlTable versionTable = table.getTable("version");
                if (versionTable != null) {
                    version = nullToEmpty(versionTable.getString("ref"));
                }
            }
            version = versions.getOrDefault(version, version);
        }
        return Optional.of(version.isBlank() ? module : module + ":" + version);
    }

    private static Optional<Path> buildFile(Path directory) {
        return firstRegularFile(directory.resolve("build.gradle"), directory.resolve("build.gradle.kts"));
    }

    private static Optional<Path> firstRegularFile(Path... paths) {
        for (Path path : paths) {
            if (Files.isRegularFile(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new MigrationExplainException("Could not read Gradle metadata for zolt explain: " + path, exception);
        }
    }

    private static String stripComments(String content) {
        String noBlockComments = content.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlockComments.replaceAll("(?m)(^|\\s)//.*$", "$1");
    }

    private static List<String> quotedValues(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Matcher matcher = QUOTED_PATTERN.matcher(input);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static Path relativePath(Path root, Path projectDirectory) {
        Path relative = root.relativize(projectDirectory);
        return relative.toString().isBlank() ? Path.of(".") : relative;
    }

    private static String projectLabel(Path root, Path projectDirectory) {
        return path(relativePath(root, projectDirectory));
    }

    private static String path(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
