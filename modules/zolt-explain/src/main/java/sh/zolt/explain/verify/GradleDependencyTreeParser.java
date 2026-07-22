package sh.zolt.explain.verify;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the text produced by one or more {@code gradle -q <path>:dependencies} tasks (typically a
 * single combined invocation covering every project) into {@link ResolvedModule}s for comparison
 * against Zolt's resolver.
 *
 * <p>Extraction mechanism and why: Gradle's {@code dependencies} report is per-project, but several
 * project tasks can be requested in one invocation, and each project's block is delimited by a
 * {@code Root project '<name>'} / {@code Project ':<path>'} title. Each block lists every configuration;
 * this parser reads the three resolvable classpath configurations and derives each project's module
 * identity from a supplied path-to-coordinates map (the report itself never prints a project's own
 * {@code group:artifact:version}).
 *
 * <p><b>Configuration to scope mapping (documented honestly, mirrored in USAGE).</b> Gradle's
 * classpaths are cumulative — {@code runtimeClasspath} and {@code testRuntimeClasspath} each contain the
 * compile dependencies — whereas Maven's {@code dependency:tree} and Zolt's lockfile place every
 * dependency in exactly one scope. Mapping the classpaths one-to-one would therefore report every
 * compile dependency as "only in Gradle" on runtime and test, so instead the four compared scopes are
 * recovered as set operations over the three resolvable classpaths:
 * <ul>
 *   <li>{@code compile} = {@code compileClasspath} ∩ {@code runtimeClasspath} (api + implementation)
 *   <li>{@code runtime} = {@code runtimeClasspath} \ {@code compileClasspath} (runtimeOnly)
 *   <li>{@code provided} = {@code compileClasspath} \ {@code runtimeClasspath} (compileOnly — the exact
 *       Maven {@code provided} equivalent, resolved via the compile classpath rather than the
 *       unresolvable {@code compileOnly} configuration)
 *   <li>{@code test} = {@code testRuntimeClasspath} \ ({@code compileClasspath} ∪ {@code runtimeClasspath})
 * </ul>
 * When {@code runtimeClasspath} is absent the whole compile classpath is treated as {@code compile} and
 * nothing is inferred as {@code provided}. Annotation-processor and other non-classpath configurations
 * are not compared (Maven's {@code dependency:tree} likewise omits processors; Zolt surfaces its own
 * {@code processor} scope as a note on its side).
 *
 * <p>Tree notation handled: {@code +---}/{@code \---}/{@code |} connectors (depth = prefix length / 5);
 * {@code a:b:req -> resolved} conflict resolution (the resolved right-hand side wins); {@code (*)}
 * repeated-subtree markers (the node is a real dependency, deduplicated by identity); {@code (c)}
 * constraints and {@code (n)} unresolvable entries (excluded — a constraint is not a dependency); BOM/
 * platform nodes, detected as a node whose direct children are all {@code (c)} constraints and excluded
 * by identity so the resolved set stays jar-for-jar comparable with Maven's {@code dependency:tree};
 * and {@code project :x} entries, mapped to the target project's coordinate (mirroring how Maven lists
 * reactor siblings as ordinary resolved artifacts).
 */
public final class GradleDependencyTreeParser {

    private static final Pattern ROOT_TITLE = Pattern.compile("^Root project '(.*)'$");
    private static final Pattern PROJECT_TITLE = Pattern.compile("^Project '(.*)'$");
    private static final Pattern SECTION_HEADER = Pattern.compile("^([A-Za-z][A-Za-z0-9]*) - .*$");
    private static final String ROOT_PATH = ":";

    /** The three resolvable classpath configurations the scope set-operations are computed over. */
    private enum Classpath {
        COMPILE,
        RUNTIME,
        TEST
    }

    public List<ResolvedModule> parse(String reportText, Map<String, GradleProjectCoordinates> projectsByPath) {
        List<ResolvedModule> modules = new ArrayList<>();
        if (reportText == null || reportText.isBlank()) {
            return modules;
        }
        Map<String, GradleProjectCoordinates> projects = projectsByPath == null ? Map.of() : projectsByPath;
        String currentPath = null;
        List<String> block = new ArrayList<>();
        for (String rawLine : reportText.split("\n", -1)) {
            String line = stripTrailingCarriageReturn(rawLine);
            String title = titlePath(line.strip());
            if (title != null) {
                if (currentPath != null) {
                    modules.add(parseBlock(currentPath, block, projects));
                }
                currentPath = title;
                block = new ArrayList<>();
                continue;
            }
            if (currentPath != null) {
                block.add(line);
            }
        }
        if (currentPath != null) {
            modules.add(parseBlock(currentPath, block, projects));
        }
        return modules;
    }

    private static String titlePath(String strippedLine) {
        Matcher root = ROOT_TITLE.matcher(strippedLine);
        if (root.matches()) {
            return ROOT_PATH;
        }
        Matcher project = PROJECT_TITLE.matcher(strippedLine);
        if (project.matches()) {
            String path = project.group(1).strip();
            return path.isEmpty() ? ROOT_PATH : path;
        }
        return null;
    }

    private ResolvedModule parseBlock(
            String projectPath, List<String> lines, Map<String, GradleProjectCoordinates> projects) {
        GradleProjectCoordinates self = projects.getOrDefault(
                projectPath, new GradleProjectCoordinates("", fallbackArtifact(projectPath), ""));
        Map<Classpath, Map<String, ResolvedArtifact>> classpaths = new EnumMap<>(Classpath.class);
        Set<Classpath> seen = EnumSet.noneOf(Classpath.class);

        Classpath active = null;
        List<GradleTreeNode> sectionNodes = new ArrayList<>();
        for (String line : lines) {
            String stripped = line.strip();
            Matcher header = SECTION_HEADER.matcher(stripped);
            if (header.matches()) {
                flush(classpaths, active, sectionNodes, self, projects);
                sectionNodes = new ArrayList<>();
                active = classpathFor(header.group(1));
                if (active != null) {
                    seen.add(active);
                }
                continue;
            }
            if (stripped.isEmpty() || stripped.startsWith("---") || stripped.equals("No dependencies")) {
                flush(classpaths, active, sectionNodes, self, projects);
                sectionNodes = new ArrayList<>();
                active = null;
                continue;
            }
            GradleTreeNode node = GradleTreeNode.parse(line);
            if (node != null && active != null) {
                sectionNodes.add(node);
            }
        }
        flush(classpaths, active, sectionNodes, self, projects);

        Map<VerifyScope, List<ResolvedArtifact>> scopes = deriveScopes(classpaths, seen);
        return new ResolvedModule(self.group(), self.artifact(), self.version(), "jar", scopes, Map.of());
    }

    private static void flush(
            Map<Classpath, Map<String, ResolvedArtifact>> classpaths,
            Classpath active,
            List<GradleTreeNode> nodes,
            GradleProjectCoordinates self,
            Map<String, GradleProjectCoordinates> projects) {
        if (active == null || nodes.isEmpty()) {
            return;
        }
        Map<String, ResolvedArtifact> bucket = classpaths.computeIfAbsent(active, key -> new LinkedHashMap<>());
        Set<String> platformKeys = GradleTreeNode.platformKeys(nodes);
        for (GradleTreeNode node : nodes) {
            if ("(c)".equals(node.marker()) || "(n)".equals(node.marker())) {
                continue;
            }
            ResolvedArtifact artifact = node.toArtifact(projects);
            if (artifact == null) {
                continue;
            }
            String key = artifact.key();
            if (platformKeys.contains(key) || key.equals(self.moduleKey())) {
                continue;
            }
            bucket.putIfAbsent(key, artifact);
        }
    }

    /**
     * Derives the four compared scopes from the resolvable classpaths as set operations, so the buckets
     * are non-cumulative (one scope per dependency) like Maven's tree and Zolt's lockfile.
     */
    private static Map<VerifyScope, List<ResolvedArtifact>> deriveScopes(
            Map<Classpath, Map<String, ResolvedArtifact>> classpaths, Set<Classpath> seen) {
        Map<String, ResolvedArtifact> compile = classpaths.getOrDefault(Classpath.COMPILE, Map.of());
        Map<String, ResolvedArtifact> runtime = classpaths.getOrDefault(Classpath.RUNTIME, Map.of());
        Map<String, ResolvedArtifact> test = classpaths.getOrDefault(Classpath.TEST, Map.of());
        boolean runtimeSeen = seen.contains(Classpath.RUNTIME);

        List<ResolvedArtifact> compileScope = new ArrayList<>();
        List<ResolvedArtifact> providedScope = new ArrayList<>();
        for (Map.Entry<String, ResolvedArtifact> entry : compile.entrySet()) {
            if (!runtimeSeen || runtime.containsKey(entry.getKey())) {
                compileScope.add(entry.getValue());
            } else {
                providedScope.add(entry.getValue());
            }
        }
        List<ResolvedArtifact> runtimeScope = new ArrayList<>();
        for (Map.Entry<String, ResolvedArtifact> entry : runtime.entrySet()) {
            if (!compile.containsKey(entry.getKey())) {
                runtimeScope.add(entry.getValue());
            }
        }
        List<ResolvedArtifact> testScope = new ArrayList<>();
        for (Map.Entry<String, ResolvedArtifact> entry : test.entrySet()) {
            if (!compile.containsKey(entry.getKey()) && !runtime.containsKey(entry.getKey())) {
                testScope.add(entry.getValue());
            }
        }

        Map<VerifyScope, List<ResolvedArtifact>> scopes = new EnumMap<>(VerifyScope.class);
        putIfNotEmpty(scopes, VerifyScope.COMPILE, compileScope);
        putIfNotEmpty(scopes, VerifyScope.RUNTIME, runtimeScope);
        putIfNotEmpty(scopes, VerifyScope.TEST, testScope);
        putIfNotEmpty(scopes, VerifyScope.PROVIDED, providedScope);
        return scopes;
    }

    private static void putIfNotEmpty(
            Map<VerifyScope, List<ResolvedArtifact>> scopes, VerifyScope scope, List<ResolvedArtifact> artifacts) {
        if (!artifacts.isEmpty()) {
            scopes.put(scope, artifacts);
        }
    }

    private static Classpath classpathFor(String configuration) {
        return switch (configuration.toLowerCase(Locale.ROOT)) {
            case "compileclasspath" -> Classpath.COMPILE;
            case "runtimeclasspath" -> Classpath.RUNTIME;
            case "testruntimeclasspath" -> Classpath.TEST;
            default -> null;
        };
    }

    private static String fallbackArtifact(String projectPath) {
        if (projectPath == null || projectPath.equals(ROOT_PATH) || projectPath.isBlank()) {
            return "";
        }
        String trimmed = projectPath.replaceFirst("^:+", "");
        int colon = trimmed.lastIndexOf(':');
        return colon >= 0 ? trimmed.substring(colon + 1) : trimmed;
    }

    private static String stripTrailingCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
