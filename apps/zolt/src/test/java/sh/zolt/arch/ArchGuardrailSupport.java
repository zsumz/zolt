package sh.zolt.arch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

final class ArchGuardrailSupport {
    private ArchGuardrailSupport() {
    }

    static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (isWorkspaceRoot(current)) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from current working directory.");
    }

    static List<Path> mainJavaFiles(Path root) throws IOException {
        return javaFiles(sourceRoots(root, "src/main/java"));
    }

    static List<Path> testJavaFiles(Path root) throws IOException {
        return javaFiles(sourceRoots(root, "src/test/java"));
    }

    static List<Path> javaFilesUnder(Path root, String relativeDirectory) throws IOException {
        Path directory = root.resolve(relativeDirectory);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    static List<Path> zoltTomlFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        Path rootToml = root.resolve("zolt.toml");
        if (Files.isRegularFile(rootToml)) {
            files.add(rootToml);
        }
        for (String parent : List.of("apps", "modules")) {
            Path parentPath = root.resolve(parent);
            if (!Files.isDirectory(parentPath)) {
                continue;
            }
            try (Stream<Path> children = Files.list(parentPath)) {
                children.map(child -> child.resolve("zolt.toml"))
                        .filter(Files::isRegularFile)
                        .sorted()
                        .forEach(files::add);
            }
        }
        return files.stream().sorted().toList();
    }

    static String relativePath(Path root, Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    static PathAllowlist pathAllowlist(Path root, String resourcePath) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        readAllowlistLines(root, resourcePath, errors).forEach(line -> {
            List<String> parts = splitAllowlistLine(line.content(), 2);
            if (parts.size() != 2 || parts.get(1).isBlank()) {
                errors.add(line.number() + ": expected `<path> | <reason>`.");
                return;
            }
            entries.put(parts.get(0), parts.get(1));
        });
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Malformed allowlist " + resourcePath + ":\n" + String.join("\n", errors));
        }
        return new PathAllowlist(entries);
    }

    static RuleAllowlist ruleAllowlist(Path root, String resourcePath) throws IOException {
        Set<RuleKey> entries = new TreeSet<>();
        List<String> errors = new ArrayList<>();
        readAllowlistLines(root, resourcePath, errors).forEach(line -> {
            List<String> parts = splitAllowlistLine(line.content(), 3);
            if (parts.size() != 3 || parts.get(2).isBlank()) {
                errors.add(line.number() + ": expected `<rule-id> | <path> | <reason>`.");
                return;
            }
            entries.add(new RuleKey(parts.get(0), parts.get(1)));
        });
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Malformed allowlist " + resourcePath + ":\n" + String.join("\n", errors));
        }
        return new RuleAllowlist(entries);
    }

    private static List<AllowlistLine> readAllowlistLines(
            Path root, String resourcePath, List<String> errors) throws IOException {
        Path path = root.resolve(resourcePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Missing allowlist resource: " + resourcePath);
        }
        List<AllowlistLine> entries = new ArrayList<>();
        List<String> lines = Files.readAllLines(path);
        for (int index = 0; index < lines.size(); index++) {
            String line = stripComment(lines.get(index)).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!line.contains("|")) {
                errors.add((index + 1) + ": expected `|` separators.");
                continue;
            }
            entries.add(new AllowlistLine(index + 1, line));
        }
        return entries;
    }

    private static List<String> splitAllowlistLine(String line, int expectedParts) {
        String[] rawParts = line.split("\\|", expectedParts);
        List<String> parts = new ArrayList<>();
        for (String rawPart : rawParts) {
            parts.add(rawPart.trim());
        }
        return parts;
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment < 0 ? line : line.substring(0, comment);
    }

    private static List<Path> sourceRoots(Path root, String suffix) throws IOException {
        List<Path> sourceRoots = new ArrayList<>();
        for (String parent : List.of("apps", "modules")) {
            Path parentPath = root.resolve(parent);
            if (!Files.isDirectory(parentPath)) {
                continue;
            }
            try (Stream<Path> children = Files.list(parentPath)) {
                children.map(child -> child.resolve(suffix))
                        .filter(Files::isDirectory)
                        .sorted()
                        .forEach(sourceRoots::add);
            }
        }
        return sourceRoots;
    }

    private static List<Path> javaFiles(List<Path> sourceRoots) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path sourceRoot : sourceRoots) {
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .forEach(files::add);
            }
        }
        files.sort(Comparator.naturalOrder());
        return List.copyOf(files);
    }

    private static boolean isWorkspaceRoot(Path current) {
        Path rootConfig = current.resolve("zolt.toml");
        if (!Files.isRegularFile(rootConfig)) {
            return false;
        }
        try {
            return Files.readString(rootConfig).lines().map(String::trim).anyMatch("[workspace]"::equals);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read root config at " + rootConfig + ".", exception);
        }
    }

    record PathAllowlist(Map<String, String> entries) {
        PathAllowlist {
            entries = Map.copyOf(entries);
        }

        boolean contains(String path) {
            return entries.containsKey(path);
        }

        Set<String> paths() {
            return entries.keySet();
        }
    }

    record RuleAllowlist(Set<RuleKey> entries) {
        RuleAllowlist {
            entries = Set.copyOf(entries);
        }

        boolean contains(String ruleId, String path) {
            return entries.contains(new RuleKey(ruleId, path));
        }
    }

    record RuleKey(String ruleId, String path) implements Comparable<RuleKey> {
        @Override
        public int compareTo(RuleKey other) {
            int ruleComparison = ruleId.compareTo(other.ruleId);
            return ruleComparison != 0 ? ruleComparison : path.compareTo(other.path);
        }
    }

    private record AllowlistLine(int number, String content) {
    }
}
