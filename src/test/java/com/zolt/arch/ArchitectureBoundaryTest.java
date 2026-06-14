package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArchitectureBoundaryTest {
    private static final Path MAIN_SOURCES = Path.of("src/main/java");

    /*
     * Architecture tests are product guardrails: Zolt owns resolver, lockfile,
     * classpath, build, package, CLI, and framework integration boundaries.
     * These checks keep refactor debt explicit while preventing accidental new
     * core cycles or framework leakage.
     */
    private static final Map<Set<String>, String> TEMPORARY_ALLOWED_CYCLES = allowedCycles();
    private static final Map<PackageEdge, String> TEMPORARY_ALLOWED_FORBIDDEN_IMPORTS =
            allowedForbiddenImports();
    private static final Set<PackageEdge> FORBIDDEN_IMPORTS = Set.of(
            new PackageEdge("com.zolt.build", "com.zolt.quarkus"),
            new PackageEdge("com.zolt.lockfile", "com.zolt.classpath"),
            new PackageEdge("com.zolt.resolve", "com.zolt.quarkus"));

    @Test
    void mainSourcesDoNotIntroduceNewPackageCycles() throws IOException {
        PackageGraph graph = PackageGraph.scan(MAIN_SOURCES);
        List<Set<String>> unexpectedCycles = graph.stronglyConnectedComponents().stream()
                .filter(component -> component.size() > 1)
                .filter(component -> !TEMPORARY_ALLOWED_CYCLES.containsKey(component))
                .sorted(Comparator.comparing(component -> String.join(",", component)))
                .toList();

        assertTrue(
                unexpectedCycles.isEmpty(),
                () -> "Unexpected package cycle(s):\n" + describeCycles(graph, unexpectedCycles)
                        + "\nAllowed temporary cycles:\n" + describeAllowedCycles());
    }

    @Test
    void forbiddenImportsAreEitherAbsentOrExplicitlyAllowlisted() throws IOException {
        PackageGraph graph = PackageGraph.scan(MAIN_SOURCES);
        Set<PackageEdge> edges = graph.edges();
        List<PackageEdge> violations = FORBIDDEN_IMPORTS.stream()
                .filter(edges::contains)
                .filter(edge -> !TEMPORARY_ALLOWED_FORBIDDEN_IMPORTS.containsKey(edge))
                .sorted()
                .toList();
        List<PackageEdge> staleAllowlist = TEMPORARY_ALLOWED_FORBIDDEN_IMPORTS.keySet().stream()
                .filter(edge -> !edges.contains(edge))
                .sorted()
                .toList();

        assertTrue(
                violations.isEmpty() && staleAllowlist.isEmpty(),
                () -> {
                    StringBuilder message = new StringBuilder();
                    if (!violations.isEmpty()) {
                        message.append("Forbidden package import(s):\n");
                        for (PackageEdge violation : violations) {
                            message.append("- ").append(violation.describe()).append('\n');
                        }
                    }
                    if (!staleAllowlist.isEmpty()) {
                        message.append("Stale architecture allowlist entries; remove them:\n");
                        for (PackageEdge edge : staleAllowlist) {
                            message.append("- ")
                                    .append(edge.describe())
                                    .append(" [")
                                    .append(TEMPORARY_ALLOWED_FORBIDDEN_IMPORTS.get(edge))
                                    .append("]\n");
                        }
                    }
                    message.append("Allowed temporary forbidden imports:\n")
                            .append(describeAllowedForbiddenImports());
                    return message.toString();
                });
    }

    @Test
    void scannerBuildsPackageEdgesFromJavaImports(@TempDir Path tempDir) throws IOException {
        write(
                tempDir.resolve("a/A.java"),
                """
                package com.zolt.alpha;

                import com.zolt.beta.Beta;
                import static com.zolt.gamma.Gamma.value;

                final class A {}
                """);
        write(
                tempDir.resolve("b/B.java"),
                """
                package com.zolt.beta.internal;

                import com.zolt.beta.Sibling;
                import java.util.List;

                final class B {}
                """);

        PackageGraph graph = PackageGraph.scan(tempDir);

        assertEquals(
                Set.of(
                        new PackageEdge("com.zolt.alpha", "com.zolt.beta"),
                        new PackageEdge("com.zolt.alpha", "com.zolt.gamma")),
                graph.edges());
    }

    @Test
    void cyclePathIsCompactAndReadable() {
        PackageGraph graph = new PackageGraph(Map.of(
                "com.zolt.a", Set.of("com.zolt.b"),
                "com.zolt.b", Set.of("com.zolt.c"),
                "com.zolt.c", Set.of("com.zolt.a")));

        assertEquals(
                "com.zolt.a -> com.zolt.b -> com.zolt.c -> com.zolt.a",
                graph.cyclePath(Set.of("com.zolt.a", "com.zolt.b", "com.zolt.c")));
    }

    private static Map<Set<String>, String> allowedCycles() {
        Map<Set<String>, String> cycles = new LinkedHashMap<>();
        cycles.put(sortedSet("com.zolt.build", "com.zolt.junit"), "");
        cycles.put(sortedSet("com.zolt.lockfile", "com.zolt.resolve"), "");
        return Map.copyOf(cycles);
    }

    private static Map<PackageEdge, String> allowedForbiddenImports() {
        return Map.of();
    }

    private static String describeCycles(PackageGraph graph, List<Set<String>> cycles) {
        StringBuilder description = new StringBuilder();
        for (Set<String> cycle : cycles) {
            description.append("- ")
                    .append(graph.cyclePath(cycle))
                    .append(" packages=")
                    .append(cycle)
                    .append('\n');
        }
        return description.toString();
    }

    private static String describeAllowedCycles() {
        StringBuilder description = new StringBuilder();
        TEMPORARY_ALLOWED_CYCLES.entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.join(",", entry.getKey())))
                .forEach(entry -> description
                        .append("- ")
                        .append(entry.getKey())
                        .append(" [")
                        .append(entry.getValue())
                        .append("]\n"));
        return description.toString();
    }

    private static String describeAllowedForbiddenImports() {
        StringBuilder description = new StringBuilder();
        TEMPORARY_ALLOWED_FORBIDDEN_IMPORTS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> description
                        .append("- ")
                        .append(entry.getKey().describe())
                        .append(" [")
                        .append(entry.getValue())
                        .append("]\n"));
        return description.toString();
    }

    private static Set<String> sortedSet(String... values) {
        return Collections.unmodifiableSet(new TreeSet<>(List.of(values)));
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }

    private record PackageEdge(String source, String target) implements Comparable<PackageEdge> {
        private String describe() {
            return source + " -> " + target;
        }

        @Override
        public int compareTo(PackageEdge other) {
            int sourceComparison = source.compareTo(other.source);
            if (sourceComparison != 0) {
                return sourceComparison;
            }
            return target.compareTo(other.target);
        }
    }

    private record SourceFile(String packageName, Set<String> importedPackages) {
    }

    private static final class SourceFileParser {
        private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
        private static final Pattern IMPORT_PATTERN =
                Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)(?:\\.\\*)?\\s*;");

        private SourceFileParser() {
        }

        private static SourceFile parse(Path path) throws IOException {
            String packageName = "";
            Set<String> importedPackages = new TreeSet<>();
            for (String line : Files.readAllLines(path)) {
                Matcher packageMatcher = PACKAGE_PATTERN.matcher(line);
                if (packageMatcher.matches()) {
                    packageName = topLevelZoltPackage(packageMatcher.group(1)).orElse("");
                    continue;
                }
                Matcher importMatcher = IMPORT_PATTERN.matcher(line);
                if (importMatcher.matches()) {
                    topLevelZoltPackage(importMatcher.group(1)).ifPresent(importedPackages::add);
                }
            }
            return new SourceFile(packageName, importedPackages);
        }

        private static Optional<String> topLevelZoltPackage(String name) {
            if (!name.startsWith("com.zolt.")) {
                return Optional.empty();
            }
            String[] parts = name.split("\\.");
            if (parts.length < 3) {
                return Optional.empty();
            }
            return Optional.of(parts[0] + "." + parts[1] + "." + parts[2]);
        }
    }

    private static final class PackageGraph {
        private final Map<String, Set<String>> edges;

        private PackageGraph(Map<String, Set<String>> edges) {
            this.edges = sortedEdges(edges);
        }

        private static PackageGraph scan(Path sourceRoot) throws IOException {
            Map<String, Set<String>> edges = new TreeMap<>();
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                List<Path> javaFiles = paths
                        .filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .toList();
                for (Path javaFile : javaFiles) {
                    SourceFile source = SourceFileParser.parse(javaFile);
                    if (source.packageName().isBlank()) {
                        continue;
                    }
                    edges.computeIfAbsent(source.packageName(), ignored -> new TreeSet<>());
                    for (String importedPackage : source.importedPackages()) {
                        if (!source.packageName().equals(importedPackage)) {
                            edges.computeIfAbsent(source.packageName(), ignored -> new TreeSet<>())
                                    .add(importedPackage);
                            edges.computeIfAbsent(importedPackage, ignored -> new TreeSet<>());
                        }
                    }
                }
            }
            return new PackageGraph(edges);
        }

        private Set<PackageEdge> edges() {
            Set<PackageEdge> result = new TreeSet<>();
            for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
                for (String target : entry.getValue()) {
                    result.add(new PackageEdge(entry.getKey(), target));
                }
            }
            return result;
        }

        private List<Set<String>> stronglyConnectedComponents() {
            List<String> packages = new ArrayList<>(edges.keySet());
            Set<String> visited = new HashSet<>();
            List<String> finishOrder = new ArrayList<>();
            for (String packageName : packages) {
                visit(packageName, visited, finishOrder);
            }

            Map<String, Set<String>> reversed = reversed();
            Collections.reverse(finishOrder);
            visited.clear();
            List<Set<String>> components = new ArrayList<>();
            for (String packageName : finishOrder) {
                if (visited.add(packageName)) {
                    Set<String> component = new TreeSet<>();
                    collect(packageName, reversed, visited, component);
                    components.add(Collections.unmodifiableSet(component));
                }
            }
            return List.copyOf(components);
        }

        private String cyclePath(Set<String> component) {
            List<String> packages = new ArrayList<>(component);
            Collections.sort(packages);
            String start = packages.get(0);
            for (String next : edges.getOrDefault(start, Set.of())) {
                if (!component.contains(next)) {
                    continue;
                }
                List<String> returnPath = path(next, start, component);
                if (!returnPath.isEmpty()) {
                    List<String> cycle = new ArrayList<>();
                    cycle.add(start);
                    cycle.addAll(returnPath);
                    return String.join(" -> ", cycle);
                }
            }
            return String.join(" -> ", packages) + " -> " + start;
        }

        private List<String> path(String start, String target, Set<String> allowedPackages) {
            ArrayDeque<List<String>> queue = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            queue.add(List.of(start));
            visited.add(start);
            while (!queue.isEmpty()) {
                List<String> path = queue.removeFirst();
                String current = path.get(path.size() - 1);
                if (current.equals(target)) {
                    return path;
                }
                for (String next : edges.getOrDefault(current, Set.of())) {
                    if (!allowedPackages.contains(next) || !visited.add(next)) {
                        continue;
                    }
                    List<String> extended = new ArrayList<>(path);
                    extended.add(next);
                    queue.addLast(extended);
                }
            }
            return List.of();
        }

        private void visit(String packageName, Set<String> visited, List<String> finishOrder) {
            if (!visited.add(packageName)) {
                return;
            }
            for (String target : edges.getOrDefault(packageName, Set.of())) {
                visit(target, visited, finishOrder);
            }
            finishOrder.add(packageName);
        }

        private void collect(
                String packageName,
                Map<String, Set<String>> graph,
                Set<String> visited,
                Set<String> component) {
            component.add(packageName);
            for (String target : graph.getOrDefault(packageName, Set.of())) {
                if (visited.add(target)) {
                    collect(target, graph, visited, component);
                }
            }
        }

        private Map<String, Set<String>> reversed() {
            Map<String, Set<String>> reversed = new TreeMap<>();
            for (String packageName : edges.keySet()) {
                reversed.computeIfAbsent(packageName, ignored -> new TreeSet<>());
            }
            for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
                for (String target : entry.getValue()) {
                    reversed.computeIfAbsent(target, ignored -> new TreeSet<>()).add(entry.getKey());
                }
            }
            return sortedEdges(reversed);
        }

        private static Map<String, Set<String>> sortedEdges(Map<String, Set<String>> input) {
            Map<String, Set<String>> result = new TreeMap<>();
            for (Map.Entry<String, Set<String>> entry : input.entrySet()) {
                result.put(entry.getKey(), Collections.unmodifiableSet(new TreeSet<>(entry.getValue())));
            }
            return Collections.unmodifiableMap(result);
        }
    }
}
