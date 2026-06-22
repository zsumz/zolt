package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ArchitectureBoundaryTest {
    private static final List<Path> MAIN_SOURCES = mainSourceRoots();
    private static final Path ARCHITECTURE_DOC = RepositoryPaths.root().resolve("docs/architecture.md");

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
            new PackageEdge("com.zolt.classpath", "com.zolt.resolve"),
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
    void architectureDocListsProductionPackages() throws IOException {
        PackageGraph graph = PackageGraph.scan(MAIN_SOURCES);
        String architectureDoc = Files.readString(ARCHITECTURE_DOC);
        List<String> missingPackages = graph.packages().stream()
                .filter(packageName -> !architectureDoc.contains(packageName))
                .toList();

        assertTrue(
                missingPackages.isEmpty(),
                () -> ARCHITECTURE_DOC
                        + " must name every top-level production package:\n"
                        + describeMissingPackages(missingPackages));
    }

    private static Map<Set<String>, String> allowedCycles() {
        return Map.of();
    }

    private static Map<PackageEdge, String> allowedForbiddenImports() {
        return Map.of();
    }

    private static List<Path> mainSourceRoots() {
        Path root = RepositoryPaths.root();
        List<Path> sourceRoots = new ArrayList<>();
        sourceRoots.add(root.resolve("apps/zolt/src/main/java"));
        try (var paths = Files.list(root.resolve("modules"))) {
            paths.map(path -> path.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .sorted()
                    .forEach(sourceRoots::add);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not list library source roots.", exception);
        }
        return List.copyOf(sourceRoots);
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

    private static String describeMissingPackages(List<String> packageNames) {
        StringBuilder description = new StringBuilder();
        for (String packageName : packageNames) {
            description.append("- ").append(packageName).append('\n');
        }
        return description.toString();
    }

}
