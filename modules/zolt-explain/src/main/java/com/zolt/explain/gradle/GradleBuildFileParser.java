package com.zolt.explain.gradle;

import com.zolt.explain.ExplainSignal;
import com.zolt.explain.ExplainSignals;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GradleBuildFileParser {
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
    private static final Pattern ID_PLUGIN_PATTERN = Pattern.compile("\\bid\\s*(?:\\(\\s*)?['\"]([^'\"]+)['\"]\\s*\\)?(?:\\s*version\\s*['\"]([^'\"]+)['\"])?");
    private static final Pattern GROOVY_PLUGIN_PATTERN = Pattern.compile("(?m)^\\s*([A-Za-z][A-Za-z0-9_-]*)\\s*$");
    // Kotlin-DSL backtick accessor form, e.g. `java-library`, `application`, `java`.
    private static final Pattern BACKTICK_PLUGIN_PATTERN = Pattern.compile("`([A-Za-z][A-Za-z0-9_.-]*)`");
    private static final Pattern REPOSITORY_URL_PATTERN = Pattern.compile("\\burl\\s*(?:=\\s*)?(?:uri\\s*\\(\\s*)?['\"]([^'\"]+)['\"]");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("['\"]([^'\"]+)['\"]");

    List<GradlePluginInspection> plugins(String content) {
        String block = GradleScriptBlocks.topLevelBlock(content, "plugins").orElse("");
        List<GradlePluginInspection> plugins = new ArrayList<>();
        Matcher idMatcher = ID_PLUGIN_PATTERN.matcher(block);
        while (idMatcher.find()) {
            plugins.add(new GradlePluginInspection(idMatcher.group(1), nullToEmpty(idMatcher.group(2))));
        }
        Matcher backtickMatcher = BACKTICK_PLUGIN_PATTERN.matcher(block);
        while (backtickMatcher.find()) {
            String id = backtickMatcher.group(1);
            if (plugins.stream().noneMatch(plugin -> plugin.id().equals(id))) {
                plugins.add(new GradlePluginInspection(id, ""));
            }
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

    List<GradleRepositoryInspection> repositories(String content) {
        return repositoriesFromBlocks(repositoryBlocks(content));
    }

    List<String> repositoryBlocks(String content) {
        return GradleScriptBlocks.topLevelBlocks(content, "repositories");
    }

    List<GradleRepositoryInspection> settingsRepositories(String content) {
        return repositoriesFromBlocks(GradleScriptBlocks.blocksAtPath(
                content,
                List.of("dependencyResolutionManagement", "repositories")));
    }

    private static List<GradleRepositoryInspection> repositoriesFromBlocks(List<String> blocks) {
        List<GradleRepositoryInspection> repositories = new ArrayList<>();
        for (String block : blocks) {
            if (block.contains("mavenCentral()")) {
                addRepository(repositories, new GradleRepositoryInspection("mavenCentral", "https://repo.maven.apache.org/maven2"));
            }
            if (block.contains("gradlePluginPortal()")) {
                addRepository(repositories, new GradleRepositoryInspection("gradlePluginPortal", "https://plugins.gradle.org/m2"));
            }
            if (block.contains("google()")) {
                addRepository(repositories, new GradleRepositoryInspection("google", "https://dl.google.com/dl/android/maven2"));
            }
            if (block.contains("mavenLocal()")) {
                addRepository(repositories, new GradleRepositoryInspection("mavenLocal", "~/.m2/repository"));
            }
            Matcher urlMatcher = REPOSITORY_URL_PATTERN.matcher(block);
            while (urlMatcher.find()) {
                addRepository(repositories, new GradleRepositoryInspection("maven", urlMatcher.group(1)));
            }
        }
        repositories.sort(Comparator.comparing(GradleRepositoryInspection::kind).thenComparing(GradleRepositoryInspection::url));
        return repositories;
    }

    private static void addRepository(List<GradleRepositoryInspection> repositories, GradleRepositoryInspection repository) {
        if (!repositories.contains(repository)) {
            repositories.add(repository);
        }
    }

    List<GradleDependencyInspection> dependencies(
            String content,
            Map<String, String> versionCatalog,
            Map<String, List<String>> catalogBundles,
            String project,
            List<ExplainSignal> signals) {
        List<GradleDependencyInspection> dependencies = new ArrayList<>();
        List<String> dependencyBlocks = GradleScriptBlocks.topLevelBlocks(content, "dependencies");
        for (String configuration : DEPENDENCY_CONFIGURATIONS) {
            Pattern pattern = Pattern.compile(
                    "(?m)(?:^|[{;])[\\t ]*" + Pattern.quote(configuration) + "(?![A-Za-z0-9_])\\s*(?:\\(([^\\n]+?)\\)|([^\\n]+))");
            for (String block : dependencyBlocks) {
                String topLevelStatements = GradleScriptBlocks.withoutNestedBlocks(block);
                Matcher matcher = pattern.matcher(topLevelStatements);
                while (matcher.find()) {
                    String expression = (matcher.group(1) == null ? matcher.group(2) : matcher.group(1)).trim();
                    dependencies.addAll(dependencies(configuration, expression, versionCatalog, catalogBundles, project, signals));
                }
            }
        }
        dependencies.sort(Comparator
                .comparing(GradleDependencyInspection::configuration)
                .thenComparing(GradleDependencyInspection::notation)
                .thenComparing(GradleDependencyInspection::resolvedCoordinate));
        return dependencies;
    }

    String javaVersion(String content) {
        for (Pattern pattern : List.of(
                Pattern.compile("\\blanguageVersion\\s*=\\s*JavaLanguageVersion\\.of\\((\\d+)\\)"),
                Pattern.compile("\\bsourceCompatibility\\s*=\\s*JavaVersion\\.VERSION_([0-9_]+)"),
                Pattern.compile("\\btargetCompatibility\\s*=\\s*JavaVersion\\.VERSION_([0-9_]+)"),
                Pattern.compile("\\bsourceCompatibility\\s*=\\s*['\"]([^'\"]+)['\"]"),
                Pattern.compile("\\btargetCompatibility\\s*=\\s*['\"]([^'\"]+)['\"]"),
                Pattern.compile("\\bsourceCompatibility\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)"),
                Pattern.compile("\\btargetCompatibility\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)"))) {
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).replace('_', '.');
            }
        }
        return "unknown";
    }

    Optional<String> group(String content) {
        return topLevelStringAssignment(content, "group");
    }

    Optional<String> version(String content) {
        return topLevelStringAssignment(content, "version");
    }

    Optional<String> mainClass(String content) {
        Optional<String> topLevel = firstQuotedAfter(GradleScriptBlocks.withoutNestedBlocks(content), List.of(
                Pattern.compile("\\bmainClass\\s*(?:=|\\.set\\s*\\(|\\.value\\s*\\()\\s*['\"]([^'\"]+)['\"]"),
                Pattern.compile("\\bmainClassName\\s*=\\s*['\"]([^'\"]+)['\"]")));
        if (topLevel.isPresent()) {
            return topLevel;
        }
        return GradleScriptBlocks.topLevelBlock(content, "application")
                .flatMap(block -> firstQuotedAfter(block, List.of(
                        Pattern.compile("\\bmainClass\\s*(?:=|\\.set\\s*\\(|\\.value\\s*\\()\\s*['\"]([^'\"]+)['\"]"),
                        Pattern.compile("\\bmainClassName\\s*=\\s*['\"]([^'\"]+)['\"]"))));
    }

    /**
     * Reads a Groovy/Kotlin-DSL string assignment such as {@code group = 'com.example'} or
     * {@code version "0.3.1"}. Only literal single/double-quoted values are returned; interpolated
     * or computed values (e.g. {@code group "${applicationGroupId}"}) are treated as absent so the
     * draft falls back to a placeholder rather than emitting a broken coordinate.
     */
    private static Optional<String> stringAssignment(String content, String property) {
        return firstQuotedAfter(content, List.of(
                Pattern.compile("(?m)^[\\t ]*" + Pattern.quote(property) + "\\s*=\\s*['\"]([^'\"$]+)['\"]"),
                Pattern.compile("(?m)^[\\t ]*" + Pattern.quote(property) + "\\s+['\"]([^'\"$]+)['\"]")));
    }

    private static Optional<String> topLevelStringAssignment(String content, String property) {
        return stringAssignment(GradleScriptBlocks.withoutNestedBlocks(content), property);
    }

    private static Optional<String> firstQuotedAfter(String content, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String value = matcher.group(1).strip();
                if (!value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    List<String> sourceRoots(String content, String sourceSet, String defaultRoot) {
        Optional<String> sourceSets = GradleScriptBlocks.topLevelBlock(content, "sourceSets");
        if (sourceSets.isEmpty()) {
            return List.of(defaultRoot);
        }
        Optional<String> sourceSetBlock = GradleScriptBlocks.topLevelBlock(sourceSets.orElseThrow(), sourceSet);
        if (sourceSetBlock.isEmpty()) {
            return List.of(defaultRoot);
        }
        List<String> roots = new ArrayList<>();
        if (containsAny(sourceSetBlock.orElseThrow(), "srcDirs +=", "srcDir(")) {
            roots.add(defaultRoot);
        }
        Matcher srcDirs = Pattern.compile("\\bsrcDirs?\\s*(?:=\\s*)?(?:\\[([^]]*)]|([^\\n]+))").matcher(sourceSetBlock.orElseThrow());
        while (srcDirs.find()) {
            String value = srcDirs.group(1) == null ? srcDirs.group(2) : srcDirs.group(1);
            roots.addAll(quotedValues(value));
        }
        return roots.isEmpty() ? List.of(defaultRoot) : roots.stream().distinct().sorted().toList();
    }

    private static List<GradleDependencyInspection> dependencies(
            String configuration,
            String expression,
            Map<String, String> versionCatalog,
            Map<String, List<String>> catalogBundles,
            String project,
            List<ExplainSignal> signals) {
        String notation = expression.replaceAll("\\s+", " ").strip();
        Optional<String> mapNotation = mapNotation(notation);
        if (mapNotation.isPresent()) {
            return List.of(new GradleDependencyInspection(configuration, mapNotation.orElseThrow(), mapNotation.orElseThrow(), ""));
        }
        List<String> quoted = quotedValues(notation);
        if (!quoted.isEmpty()) {
            return quoted.stream()
                    .map(value -> new GradleDependencyInspection(configuration, value, value, ""))
                    .toList();
        }
        Optional<String> bundle = catalogBundleAlias(notation);
        if (bundle.isPresent()) {
            return bundleDependencies(configuration, notation, bundle.orElseThrow(), catalogBundles, project, signals);
        }
        List<String> aliases = catalogAliases(notation);
        if (!aliases.isEmpty()) {
            return aliases.stream()
                    .map(key -> new GradleDependencyInspection(configuration, notation, versionCatalog.getOrDefault(key, ""), key))
                    .toList();
        }
        unresolvedDependencySignal(project, configuration, notation, signals);
        return List.of();
    }

    private static void unresolvedDependencySignal(
            String project,
            String configuration,
            String notation,
            List<ExplainSignal> signals) {
        if (notation.isBlank()) {
            return;
        }
        signals.add(ExplainSignals.GRADLE_DEPENDENCY_UNRESOLVED_NOTATION.signal(
                project,
                "Gradle dependency `" + configuration + " " + notation
                        + "` is not a statically resolvable literal and was not emitted as a dependency."));
    }

    private static List<GradleDependencyInspection> bundleDependencies(
            String configuration,
            String notation,
            String bundleName,
            Map<String, List<String>> catalogBundles,
            String project,
            List<ExplainSignal> signals) {
        List<String> members = catalogBundles.get(bundleName);
        if (members == null) {
            // Bundle referenced but absent from the catalog: keep an unresolved marker instead of
            // silently dropping the dependency, so the mapper surfaces a review note.
            signals.add(ExplainSignals.GRADLE_VERSION_CATALOG_BUNDLE_UNRESOLVED.signal(
                    project,
                    "Gradle dependency references version-catalog bundle `" + bundleName
                            + "`, which is not defined in gradle/libs.versions.toml."));
            return List.of(new GradleDependencyInspection(configuration, notation, "", "bundles." + bundleName));
        }
        if (members.isEmpty()) {
            return List.of(new GradleDependencyInspection(configuration, notation, "", "bundles." + bundleName));
        }
        List<GradleDependencyInspection> expanded = new ArrayList<>();
        for (String coordinate : members) {
            expanded.add(new GradleDependencyInspection(configuration, notation, coordinate, "bundles." + bundleName));
        }
        return expanded;
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

    private static List<String> catalogAliases(String expression) {
        Matcher matcher = Pattern.compile("\\blibs\\.(?!bundles\\.)([A-Za-z0-9_.-]+)").matcher(expression);
        List<String> aliases = new ArrayList<>();
        while (matcher.find()) {
            aliases.add(matcher.group(1));
        }
        return aliases;
    }

    private static Optional<String> catalogBundleAlias(String expression) {
        Matcher matcher = Pattern.compile("\\blibs\\.bundles\\.([A-Za-z0-9_.-]+)").matcher(expression);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
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

    private static boolean containsAny(String content, String... values) {
        for (String value : values) {
            if (content.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

}
