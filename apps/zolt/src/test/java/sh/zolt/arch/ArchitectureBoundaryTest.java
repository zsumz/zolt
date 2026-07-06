package sh.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

final class ArchitectureBoundaryTest {
    private static final String ALLOWLIST = "apps/zolt/src/test/resources/sh/zolt/arch/architecture-allowlist.txt";
    private static final List<Path> MAIN_SOURCES = RepositoryPaths.mainSourceRoots();

    private static final String NO_FORBIDDEN_MAVEN_LIBRARIES = "no-forbidden-maven-libraries";
    private static final String NO_CLI_IN_CORE_MODULES = "no-cli-in-core-modules";
    private static final String NO_HTTP_CACHE_IN_GRAPH_SELECTION = "no-http-cache-in-graph-selection";

    private static final List<String> FORBIDDEN_MAVEN_SOURCE_TOKENS = List.of(
            "org.eclipse.aether.",
            "org.apache.maven.artifact.",
            "org.apache.maven.cli.",
            "org.apache.maven.embedder.",
            "org.apache.maven.execution.",
            "org.apache.maven.lifecycle.",
            "org.apache.maven.model.building.",
            "org.apache.maven.plugin.",
            "org.apache.maven.project.",
            "org.apache.maven.repository.");

    private static final List<String> FORBIDDEN_MAVEN_DEPENDENCY_TOKENS = List.of(
            "org.eclipse.aether:",
            "org.apache.maven.resolver:",
            "org.apache.maven:maven-artifact",
            "org.apache.maven:maven-compat",
            "org.apache.maven:maven-core",
            "org.apache.maven:maven-embedder",
            "org.apache.maven:maven-model",
            "org.apache.maven:maven-model-builder",
            "org.apache.maven:maven-plugin-api",
            "org.apache.maven:maven-resolver-provider");

    private static final List<String> GRAPH_SELECTION_FORBIDDEN_TOKENS = List.of(
            "java.net.http",
            "sh.zolt.cache.",
            "sh.zolt.concurrent.",
            "ArtifactFetcher",
            "ArtifactMaterializer",
            "LocalArtifactCache",
            "LocalOverlayMaterializer",
            "MavenRepositoryClient",
            "RepositoryAccess",
            "RepositoryFetch",
            "RepositoryHttp",
            "RepositorySession");

    @Test
    void mainSourcesDoNotIntroducePackageCycles() throws IOException {
        PackageGraph graph = PackageGraph.scan(MAIN_SOURCES);
        List<Set<String>> cycles = graph.stronglyConnectedComponents().stream()
                .filter(component -> component.size() > 1)
                .sorted(Comparator.comparing(component -> String.join(",", component)))
                .toList();

        assertTrue(
                cycles.isEmpty(),
                () -> "Unexpected package cycle(s):\n" + describeCycles(graph, cycles));
    }

    @Test
    void architectureBoundariesHoldOrHaveExplicitAllowance() throws IOException {
        Path root = ArchGuardrailSupport.repositoryRoot();
        ArchGuardrailSupport.RuleAllowlist allowlist = ArchGuardrailSupport.ruleAllowlist(root, ALLOWLIST);
        List<Violation> candidates = new ArrayList<>();

        candidates.addAll(forbiddenMavenLibraryViolations(root));
        candidates.addAll(coreModuleCliViolations(root));
        candidates.addAll(graphSelectionRepositoryViolations(root));

        Set<ArchGuardrailSupport.RuleKey> matchedAllowances = new TreeSet<>();
        List<Violation> unallowed = new ArrayList<>();
        for (Violation candidate : candidates) {
            if (allowlist.contains(candidate.ruleId(), candidate.path())) {
                matchedAllowances.add(new ArchGuardrailSupport.RuleKey(candidate.ruleId(), candidate.path()));
            } else {
                unallowed.add(candidate);
            }
        }

        List<String> problems = new ArrayList<>();
        unallowed.forEach(violation -> problems.add(violation.message()));
        for (ArchGuardrailSupport.RuleKey entry : allowlist.entries()) {
            if (!matchedAllowances.contains(entry)) {
                problems.add("Stale architecture allowance: " + entry.ruleId() + " | " + entry.path()
                        + " is no longer needed; remove it from " + ALLOWLIST + ".");
            }
        }

        assertTrue(problems.isEmpty(), () -> architectureMessage(problems));
    }

    private static List<Violation> forbiddenMavenLibraryViolations(Path root) throws IOException {
        List<Violation> violations = new ArrayList<>();
        for (Path file : ArchGuardrailSupport.mainJavaFiles(root)) {
            scanSourceTokens(root, file, NO_FORBIDDEN_MAVEN_LIBRARIES, FORBIDDEN_MAVEN_SOURCE_TOKENS, violations);
        }
        for (Path file : ArchGuardrailSupport.zoltTomlFiles(root)) {
            scanDependencyTokens(root, file, violations);
        }
        return violations;
    }

    private static List<Violation> coreModuleCliViolations(Path root) throws IOException {
        List<Violation> violations = new ArrayList<>();
        for (Path file : ArchGuardrailSupport.mainJavaFiles(root)) {
            if (!ArchGuardrailSupport.relativePath(root, file).startsWith("modules/")) {
                continue;
            }
            scanSourceTokens(root, file, NO_CLI_IN_CORE_MODULES, List.of("import sh.zolt.cli.", "sh.zolt.cli."), violations);
        }
        return violations;
    }

    private static List<Violation> graphSelectionRepositoryViolations(Path root) throws IOException {
        List<Violation> violations = new ArrayList<>();
        for (String relativeDirectory : List.of(
                "modules/zolt-resolve/src/main/java/sh/zolt/resolve/graph",
                "modules/zolt-resolve/src/main/java/sh/zolt/resolve/selection",
                "modules/zolt-resolve/src/main/java/sh/zolt/resolve/traversal",
                "modules/zolt-resolve/src/main/java/sh/zolt/resolve/version")) {
            for (Path file : ArchGuardrailSupport.javaFilesUnder(root, relativeDirectory)) {
                scanSourceTokens(
                        root,
                        file,
                        NO_HTTP_CACHE_IN_GRAPH_SELECTION,
                        GRAPH_SELECTION_FORBIDDEN_TOKENS,
                        violations);
            }
        }
        return violations;
    }

    private static void scanSourceTokens(
            Path root,
            Path file,
            String ruleId,
            List<String> tokens,
            List<Violation> violations) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            for (String token : tokens) {
                if (line.contains(token)) {
                    violations.add(new Violation(
                            ruleId,
                            ArchGuardrailSupport.relativePath(root, file),
                            index + 1,
                            "contains forbidden token `" + token + "`"));
                    break;
                }
            }
        }
    }

    private static void scanDependencyTokens(Path root, Path file, List<Violation> violations) throws IOException {
        String section = "";
        List<String> lines = Files.readAllLines(file);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            if (section.startsWith("test.") || !line.startsWith("\"")) {
                continue;
            }
            for (String token : FORBIDDEN_MAVEN_DEPENDENCY_TOKENS) {
                if (line.contains(token)) {
                    violations.add(new Violation(
                            NO_FORBIDDEN_MAVEN_LIBRARIES,
                            ArchGuardrailSupport.relativePath(root, file),
                            index + 1,
                            "declares forbidden dependency `" + token + "`"));
                    break;
                }
            }
        }
    }

    private static String architectureMessage(List<String> problems) {
        List<String> lines = new ArrayList<>();
        lines.add("Architecture guardrail drift.");
        lines.add("Zolt owns its model/resolver/build pipeline; do not add Maven lifecycle/embedder/resolver libraries.");
        lines.add("Core modules must not depend on CLI packages.");
        lines.add("Graph selection code must not reach HTTP/cache/repository-fetch implementation details.");
        lines.add("If an exception is deliberate, add it to " + ALLOWLIST + " with a concrete reason.");
        lines.addAll(problems);
        return String.join(System.lineSeparator(), lines);
    }

    private static String describeCycles(PackageGraph graph, List<Set<String>> cycles) {
        StringBuilder description = new StringBuilder();
        for (Set<String> cycle : cycles) {
            description.append("- ")
                    .append(graph.cyclePath(cycle))
                    .append(" packages=")
                    .append(cycle)
                    .append(System.lineSeparator());
        }
        return description.toString();
    }

    private record Violation(String ruleId, String path, int line, String detail) {
        String message() {
            return ruleId + ": " + path + ":" + line + " " + detail + ".";
        }
    }
}
