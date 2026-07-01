package com.zolt.explain.gradle;

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
    private static final Pattern REPOSITORY_URL_PATTERN = Pattern.compile("\\burl\\s*(?:=\\s*)?(?:uri\\s*\\(\\s*)?['\"]([^'\"]+)['\"]");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("['\"]([^'\"]+)['\"]");

    List<GradlePluginInspection> plugins(String content) {
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

    List<GradleRepositoryInspection> repositories(String content) {
        List<GradleRepositoryInspection> repositories = new ArrayList<>();
        if (content.contains("mavenCentral()")) {
            repositories.add(new GradleRepositoryInspection("mavenCentral", "https://repo.maven.apache.org/maven2"));
        }
        if (content.contains("gradlePluginPortal()")) {
            repositories.add(new GradleRepositoryInspection("gradlePluginPortal", "https://plugins.gradle.org/m2"));
        }
        if (content.contains("google()")) {
            repositories.add(new GradleRepositoryInspection("google", "https://dl.google.com/dl/android/maven2"));
        }
        if (content.contains("mavenLocal()")) {
            repositories.add(new GradleRepositoryInspection("mavenLocal", "~/.m2/repository"));
        }
        Matcher urlMatcher = REPOSITORY_URL_PATTERN.matcher(content);
        while (urlMatcher.find()) {
            repositories.add(new GradleRepositoryInspection("maven", urlMatcher.group(1)));
        }
        repositories.sort(Comparator.comparing(GradleRepositoryInspection::kind).thenComparing(GradleRepositoryInspection::url));
        return repositories;
    }

    List<GradleDependencyInspection> dependencies(String content, Map<String, String> versionCatalog) {
        String block = block(content, "dependencies").orElse("");
        List<GradleDependencyInspection> dependencies = new ArrayList<>();
        for (String configuration : DEPENDENCY_CONFIGURATIONS) {
            // Anchor the configuration to a statement-leading token (start of line, or after `{`/`;`)
            // and require the token to end, so a config name only matches when it is the leading
            // identifier of a dependency statement. This stops the `api` inside `slf4j-api` (a regex
            // word boundary at `-`/`:`) from spawning a phantom `api`-scope dependency.
            Pattern pattern = Pattern.compile(
                    "(?m)(?:^|[{;])[\\t ]*" + Pattern.quote(configuration) + "(?![A-Za-z0-9_])\\s*(?:\\(([^\\n]+?)\\)|([^\\n]+))");
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

    String javaVersion(String content) {
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

    Optional<String> group(String content) {
        return stringAssignment(content, "group");
    }

    Optional<String> version(String content) {
        return stringAssignment(content, "version");
    }

    Optional<String> mainClass(String content) {
        return firstQuotedAfter(content, List.of(
                Pattern.compile("\\bmainClass\\s*(?:=|\\.set\\s*\\(|\\.value\\s*\\()\\s*['\"]([^'\"]+)['\"]"),
                Pattern.compile("\\bmainClassName\\s*=\\s*['\"]([^'\"]+)['\"]")));
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
        Optional<String> sourceSets = block(content, "sourceSets");
        if (sourceSets.isEmpty()) {
            return List.of(defaultRoot);
        }
        Optional<String> sourceSetBlock = block(sourceSets.orElseThrow(), sourceSet);
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
