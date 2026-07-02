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
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class GradleStaticProjectInspector {
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
        Map<String, List<String>> catalogBundles = new LinkedHashMap<>();
        List<GradleVersionCatalogAlias> aliases = new ArrayList<>(parseVersionCatalog(
                normalizedRoot.resolve("gradle/libs.versions.toml"),
                versionCatalog,
                catalogBundles,
                signals));
        String settingsContent = settingsFile.map(GradleStaticProjectInspector::read).orElse("");
        List<String> includedProjects = settingsFile.isPresent()
                ? GradleSettingsScripts.includedProjects(settingsContent)
                : List.of();
        Optional<String> rootProjectName = settingsFile.isPresent()
                ? GradleSettingsScripts.rootProjectName(settingsContent)
                : Optional.empty();
        GradleSettingsBuildFileNames settingsBuildFileNames = GradleSettingsBuildFileNames.parse(settingsContent);
        settingsFile.ifPresent(path -> signals.addAll(GradleSettingsScripts.signals(normalizedRoot, path, settingsContent)));
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
                rootProjectName,
                versionCatalog,
                catalogBundles,
                signals)));
        for (String includedProject : includedProjects) {
            Path projectDirectory = normalizedRoot.resolve(includedProject).normalize();
            String projectName = projectDirectory.getFileName().toString();
            Optional<Path> includedBuildFile = settingsBuildFileNames.buildFile(projectDirectory, projectName);
            if (includedBuildFile.isPresent()) {
                projects.add(inspectProject(normalizedRoot, projectDirectory, includedBuildFile.orElseThrow(), Optional.empty(), versionCatalog, catalogBundles, signals));
                continue;
            }
            Optional<ExplainSignal> unresolvedBuildFileName = settingsBuildFileNames.unresolvedCandidateSignal(
                    normalizedRoot,
                    projectDirectory,
                    includedProject);
            if (unresolvedBuildFileName.isPresent()) {
                signals.add(unresolvedBuildFileName.orElseThrow());
            } else {
                String expected = settingsBuildFileNames.fileNameFor(projectName)
                        .map(name -> "`" + name + "`")
                        .orElse("build.gradle or build.gradle.kts");
                signals.add(ExplainSignals.GRADLE_PROJECT_MISSING_BUILD_FILE.signal(
                        projectLabel(normalizedRoot, projectDirectory),
                        "Included Gradle project `" + includedProject + "` does not contain " + expected + "."));
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
            Optional<String> declaredName,
            Map<String, String> versionCatalog,
            Map<String, List<String>> catalogBundles,
            List<ExplainSignal> signals) {
        String content = stripComments(read(buildFile));
        Path relativePath = relativePath(root, projectDirectory);
        String project = path(relativePath);
        List<GradlePluginInspection> plugins = buildFileParser.plugins(content);
        List<GradleDependencyInspection> dependencies =
                buildFileParser.dependencies(content, versionCatalog, catalogBundles, project, signals);
        signals.addAll(signalDetector.signals(project, content, dependencies, plugins));
        if (hasGroovyMainSources(projectDirectory, content)) {
            signals.add(ExplainSignals.GRADLE_LANGUAGE_UNSUPPORTED.signal(
                    project,
                    "Gradle project has Groovy main sources, which are outside the Zolt public beta."));
        }
        return new GradleProjectInspection(
                relativePath,
                declaredName.filter(name -> !name.isBlank()).orElseGet(() -> projectDirectory.getFileName().toString()),
                buildFile.getFileName().toString(),
                buildFile.getFileName().toString().endsWith(".kts") ? "kotlin" : "groovy",
                buildFileParser.javaVersion(content),
                buildFileParser.group(content),
                buildFileParser.version(content),
                buildFileParser.mainClass(content),
                plugins,
                buildFileParser.repositories(content),
                dependencies,
                buildFileParser.sourceRoots(content, "main", "src/main/java"),
                buildFileParser.sourceRoots(content, "test", "src/test/java"));
    }

    private static List<GradleVersionCatalogAlias> parseVersionCatalog(
            Path catalogPath,
            Map<String, String> aliases,
            Map<String, List<String>> bundles,
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
        Map<String, String> libraryCoordinatesByKey = new LinkedHashMap<>();
        TomlTable libraries = result.getTable("libraries");
        if (libraries != null) {
            for (String key : libraries.keySet()) {
                Optional<String> coordinate = libraryCoordinate(libraries, key, versions);
                if (coordinate.isPresent()) {
                    String value = coordinate.orElseThrow();
                    libraryCoordinatesByKey.put(key, value);
                    aliases.put(key, value);
                    aliases.put(key.replace('-', '.'), value);
                    parsed.add(new GradleVersionCatalogAlias(key, value));
                }
            }
        }
        parseBundles(result, libraryCoordinatesByKey, bundles, signals);
        return parsed;
    }

    private static void parseBundles(
            TomlParseResult result,
            Map<String, String> libraryCoordinatesByKey,
            Map<String, List<String>> bundles,
            List<ExplainSignal> signals) {
        TomlTable bundleTable = result.getTable("bundles");
        if (bundleTable == null) {
            return;
        }
        for (String bundle : bundleTable.keySet()) {
            org.tomlj.TomlArray memberArray = bundleTable.getArray(List.of(bundle));
            List<?> members = memberArray == null ? List.of() : memberArray.toList();
            List<String> coordinates = new ArrayList<>();
            List<String> unresolved = new ArrayList<>();
            for (Object member : members) {
                if (!(member instanceof String memberKey) || memberKey.isBlank()) {
                    continue;
                }
                String coordinate = libraryCoordinatesByKey.get(memberKey);
                if (coordinate == null) {
                    coordinate = libraryCoordinatesByKey.get(memberKey.replace('.', '-'));
                }
                if (coordinate == null) {
                    unresolved.add(memberKey);
                } else {
                    coordinates.add(coordinate);
                }
            }
            // Register under both raw and dot-normalized keys so `libs.bundles.<x>` resolves
            // regardless of whether the alias was written with `-` or `.` separators.
            bundles.put(bundle, coordinates);
            bundles.put(bundle.replace('-', '.'), coordinates);
            if (!unresolved.isEmpty()) {
                signals.add(ExplainSignals.GRADLE_VERSION_CATALOG_BUNDLE_UNRESOLVED.signal(
                        ".",
                        "Gradle version catalog bundle `" + bundle + "` references undefined libraries "
                                + unresolved + "; those members were dropped from the migration draft."));
            }
        }
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
        return GradleSourceComments.stripComments(content);
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

    private static boolean hasGroovyMainSources(Path projectDirectory, String content) {
        return Files.isDirectory(projectDirectory.resolve("src/main/groovy"))
                || content.contains("src/main/groovy");
    }

}
