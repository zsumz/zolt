package sh.zolt.arch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class PackageGraph {
    private final Map<String, Set<String>> edges;

    PackageGraph(Map<String, Set<String>> edges) {
        this.edges = sortedEdges(edges);
    }

    static PackageGraph scan(Path sourceRoot) throws IOException {
        return scan(List.of(sourceRoot));
    }

    static PackageGraph scan(List<Path> sourceRoots) throws IOException {
        Map<String, Set<String>> edges = new TreeMap<>();
        for (Path javaFile : ArchitectureSourceFiles.javaFiles(sourceRoots)) {
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
        return new PackageGraph(edges);
    }

    Set<String> packages() {
        return edges.keySet();
    }

    Set<PackageEdge> edges() {
        Set<PackageEdge> result = new TreeSet<>();
        for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
            for (String target : entry.getValue()) {
                result.add(new PackageEdge(entry.getKey(), target));
            }
        }
        return result;
    }

    List<Set<String>> stronglyConnectedComponents() {
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

    String cyclePath(Set<String> component) {
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
