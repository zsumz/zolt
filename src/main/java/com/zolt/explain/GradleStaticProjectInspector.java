package com.zolt.explain;

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
    private static final List<String> DEPENDENCY_CONFIGURATIONS = List.of(
            "api",
            "implementation",
            "compileOnly",
            "runtimeOnly",
            "testImplementation",
            "testCompileOnly",
            "testRuntimeOnly",
            "annotationProcessor",
            "testAnnotationProcessor");
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\binclude\\b\\s*(?:\\(([^)]*)\\)|([^\\n]+))");
    private static final Pattern INCLUDE_BUILD_PATTERN = Pattern.compile("\\bincludeBuild\\b\\s*(?:\\(([^)]*)\\)|([^\\n]+))");
    private static final Pattern ID_PLUGIN_PATTERN = Pattern.compile("\\bid\\s*(?:\\(\\s*)?['\"]([^'\"]+)['\"]\\s*\\)?(?:\\s*version\\s*['\"]([^'\"]+)['\"])?");
    private static final Pattern GROOVY_PLUGIN_PATTERN = Pattern.compile("(?m)^\\s*([A-Za-z][A-Za-z0-9_-]*)\\s*$");
    private static final Pattern REPOSITORY_URL_PATTERN = Pattern.compile("\\burl\\s*(?:=\\s*)?(?:uri\\s*\\(\\s*)?['\"]([^'\"]+)['\"]");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("['\"]([^'\"]+)['\"]");

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
        List<GradlePluginInspection> plugins = parsePlugins(content);
        List<GradleDependencyInspection> dependencies = parseDependencies(content, versionCatalog);
        for (GradlePluginInspection plugin : plugins) {
            if (isConventionPlugin(plugin.id())) {
                signals.add(ExplainSignals.GRADLE_PLUGIN_CONVENTION.signal(
                        project,
                        "Plugin `" + plugin.id() + "` looks like a convention plugin."));
            }
        }
        signals.addAll(dynamicSignals(project, content));
        signals.addAll(dependencySignals(project, dependencies));
        return new GradleProjectInspection(
                relativePath,
                relativePath.toString().equals(".") ? projectDirectory.getFileName().toString() : projectDirectory.getFileName().toString(),
                buildFile.getFileName().toString(),
                buildFile.getFileName().toString().endsWith(".kts") ? "kotlin" : "groovy",
                javaVersion(content),
                plugins,
                parseRepositories(content),
                dependencies,
                parseSourceRoots(content, "main", "src/main/java"),
                parseSourceRoots(content, "test", "src/test/java"));
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

    private static List<GradlePluginInspection> parsePlugins(String content) {
        String block = block(content, "plugins").orElse("");
        List<GradlePluginInspection> plugins = new ArrayList<>();
        Matcher idMatcher = ID_PLUGIN_PATTERN.matcher(block);
        while (idMatcher.find()) {
            plugins.add(new GradlePluginInspection(idMatcher.group(1), nullToEmpty(idMatcher.group(2))));
        }
        Matcher groovyMatcher = GROOVY_PLUGIN_PATTERN.matcher(block);
        while (groovyMatcher.find()) {
            String id = groovyMatcher.group(1);
            if (!"id".equals(id) && plugins.stream().noneMatch(plugin -> plugin.id().equals(id))) {
                plugins.add(new GradlePluginInspection(id, ""));
            }
        }
        plugins.sort(Comparator.comparing(GradlePluginInspection::id).thenComparing(GradlePluginInspection::version));
        return plugins;
    }

    private static List<GradleRepositoryInspection> parseRepositories(String content) {
        String block = block(content, "repositories").orElse("");
        List<GradleRepositoryInspection> repositories = new ArrayList<>();
        if (block.contains("mavenCentral()")) {
            repositories.add(new GradleRepositoryInspection("mavenCentral", "https://repo.maven.apache.org/maven2"));
        }
        if (block.contains("gradlePluginPortal()")) {
            repositories.add(new GradleRepositoryInspection("gradlePluginPortal", "https://plugins.gradle.org/m2"));
        }
        if (block.contains("google()")) {
            repositories.add(new GradleRepositoryInspection("google", "https://dl.google.com/dl/android/maven2"));
        }
        Matcher urlMatcher = REPOSITORY_URL_PATTERN.matcher(block);
        while (urlMatcher.find()) {
            repositories.add(new GradleRepositoryInspection("maven", urlMatcher.group(1)));
        }
        repositories.sort(Comparator.comparing(GradleRepositoryInspection::kind).thenComparing(GradleRepositoryInspection::url));
        return repositories;
    }

    private static List<GradleDependencyInspection> parseDependencies(
            String content,
            Map<String, String> versionCatalog) {
        String block = block(content, "dependencies").orElse("");
        List<GradleDependencyInspection> dependencies = new ArrayList<>();
        for (String configuration : DEPENDENCY_CONFIGURATIONS) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(configuration) + "\\s*(?:\\(([^\\n]+?)\\)|([^\\n]+))");
            Matcher matcher = pattern.matcher(block);
            while (matcher.find()) {
                String expression = (matcher.group(1) == null ? matcher.group(2) : matcher.group(1)).trim();
                dependencies.add(dependency(configuration, expression, versionCatalog));
            }
        }
        dependencies.sort(Comparator
                .comparing(GradleDependencyInspection::configuration)
                .thenComparing(GradleDependencyInspection::notation)
                .thenComparing(GradleDependencyInspection::resolvedCoordinate));
        return dependencies;
    }

    private static GradleDependencyInspection dependency(
            String configuration,
            String expression,
            Map<String, String> versionCatalog) {
        String notation = expression.replaceAll("\\s+", " ").strip();
        Optional<String> mapNotation = mapNotation(notation);
        if (mapNotation.isPresent()) {
            return new GradleDependencyInspection(configuration, mapNotation.orElseThrow(), mapNotation.orElseThrow(), "");
        }
        Optional<String> quoted = firstQuoted(notation);
        if (quoted.isPresent()) {
            return new GradleDependencyInspection(configuration, quoted.orElseThrow(), quoted.orElseThrow(), "");
        }
        Optional<String> alias = catalogAlias(notation);
        if (alias.isPresent()) {
            String key = alias.orElseThrow();
            return new GradleDependencyInspection(configuration, notation, versionCatalog.getOrDefault(key, ""), key);
        }
        return new GradleDependencyInspection(configuration, notation, "", "");
    }

    private static String javaVersion(String content) {
        for (Pattern pattern : List.of(
                Pattern.compile("\\blanguageVersion\\s*=\\s*JavaLanguageVersion\\.of\\((\\d+)\\)"),
                Pattern.compile("\\bsourceCompatibility\\s*=\\s*JavaVersion\\.VERSION_(\\d+)"),
                Pattern.compile("\\btargetCompatibility\\s*=\\s*JavaVersion\\.VERSION_(\\d+)"),
                Pattern.compile("\\bsourceCompatibility\\s*=\\s*['\"]([^'\"]+)['\"]"),
                Pattern.compile("\\btargetCompatibility\\s*=\\s*['\"]([^'\"]+)['\"]"))) {
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).replace('_', '.');
            }
        }
        return "unknown";
    }

    private static List<String> parseSourceRoots(String content, String sourceSet, String defaultRoot) {
        Optional<String> sourceSets = block(content, "sourceSets");
        if (sourceSets.isEmpty()) {
            return List.of(defaultRoot);
        }
        Optional<String> sourceSetBlock = block(sourceSets.orElseThrow(), sourceSet);
        if (sourceSetBlock.isEmpty()) {
            return List.of(defaultRoot);
        }
        List<String> roots = new ArrayList<>();
        Matcher srcDirs = Pattern.compile("\\bsrcDirs?\\s*(?:=\\s*)?(?:\\[([^]]*)]|([^\\n]+))").matcher(sourceSetBlock.orElseThrow());
        while (srcDirs.find()) {
            String value = srcDirs.group(1) == null ? srcDirs.group(2) : srcDirs.group(1);
            roots.addAll(quotedValues(value));
        }
        return roots.isEmpty() ? List.of(defaultRoot) : roots.stream().distinct().sorted().toList();
    }

    private static List<ExplainSignal> dynamicSignals(String project, String content) {
        List<ExplainSignal> signals = new ArrayList<>();
        if (containsAny(content, "dependencies.add(", "configurations.all", "resolutionStrategy", "afterEvaluate")) {
            signals.add(ExplainSignals.GRADLE_IMPERATIVE_DEPENDENCY_LOGIC.signal(
                    project,
                    "Gradle build uses imperative dependency or configuration mutation."));
        }
        if (Pattern.compile("\\b(subprojects|allprojects)\\s*\\{").matcher(content).find()) {
            signals.add(ExplainSignals.GRADLE_CROSS_PROJECT_BUILD_LOGIC.signal(
                    project,
                    "Gradle build uses cross-project script logic."));
        }
        if (Pattern.compile("\\btasks\\.(register|create)\\s*\\(").matcher(content).find()) {
            signals.add(ExplainSignals.GRADLE_CUSTOM_TASK_DETECTED.signal(
                    project,
                    "Gradle build declares custom tasks."));
        }
        return signals;
    }

    private static List<ExplainSignal> dependencySignals(
            String project,
            List<GradleDependencyInspection> dependencies) {
        List<ExplainSignal> signals = new ArrayList<>();
        for (GradleDependencyInspection dependency : dependencies) {
            String version = coordinateVersion(dependency.resolvedCoordinate());
            if (isDynamicVersion(version)) {
                signals.add(ExplainSignals.GRADLE_DEPENDENCY_DYNAMIC_VERSION.signal(
                        project,
                        "Dependency `" + dependency.resolvedCoordinate() + "` uses dynamic version `" + version + "`."));
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

    private static Optional<String> block(String content, String name) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\{").matcher(content);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int openBrace = content.indexOf('{', matcher.start());
        int depth = 0;
        for (int index = openBrace; index < content.length(); index++) {
            char character = content.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return Optional.of(content.substring(openBrace + 1, index));
                }
            }
        }
        return Optional.of(content.substring(openBrace + 1));
    }

    private static String stripComments(String content) {
        String noBlockComments = content.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlockComments.replaceAll("(?m)//.*$", "");
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

    private static Optional<String> firstQuoted(String value) {
        Matcher matcher = QUOTED_PATTERN.matcher(value);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static Optional<String> mapNotation(String value) {
        String group = namedArgument(value, "group");
        String name = namedArgument(value, "name");
        String version = namedArgument(value, "version");
        if (group.isBlank() || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(version.isBlank() ? group + ":" + name : group + ":" + name + ":" + version);
    }

    private static String namedArgument(String expression, String name) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*(?::|=)\\s*['\"]([^'\"]+)['\"]").matcher(expression);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static Optional<String> catalogAlias(String expression) {
        Matcher matcher = Pattern.compile("\\blibs\\.([A-Za-z0-9_.-]+)").matcher(expression);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static boolean isConventionPlugin(String id) {
        String lower = id.toLowerCase();
        return lower.contains("convention") || lower.contains("build-logic");
    }

    private static boolean containsAny(String content, String... values) {
        for (String value : values) {
            if (content.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String coordinateVersion(String coordinate) {
        int lastColon = coordinate.lastIndexOf(':');
        if (lastColon < 0 || lastColon == coordinate.length() - 1) {
            return "";
        }
        return coordinate.substring(lastColon + 1);
    }

    private static boolean isDynamicVersion(String version) {
        if (version.isBlank()) {
            return false;
        }
        String lower = version.toLowerCase();
        return lower.contains("+") || lower.startsWith("latest.") || lower.endsWith("-snapshot");
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
