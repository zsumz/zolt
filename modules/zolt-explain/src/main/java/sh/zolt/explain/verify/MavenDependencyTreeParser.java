package sh.zolt.explain.verify;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Parses the text produced by {@code mvn dependency:tree -DoutputType=text -DoutputFile=... }
 * {@code -DappendOutput=true} over a (possibly multi-module) reactor into {@link ResolvedModule}s.
 *
 * <p>Extraction mechanism and why: a single reactor invocation of {@code dependency:tree} with an
 * {@code outputFile} writes each module's resolved tree to one file, in reactor order. Writing to a
 * file (rather than the log) means the output survives {@code -q} and carries no {@code [INFO]}
 * prefixes. In non-verbose mode the tree contains only the resolved winners after Maven's
 * nearest-wins mediation — every node is a real resolved dependency, so no "omitted for conflict"
 * bookkeeping is needed. Crucially, each module block begins with a zero-indent root line carrying
 * the module's own {@code group:artifact:packaging:version}, which gives per-module identity from one
 * invocation.
 *
 * <p>Line grammar:
 * <ul>
 *   <li>root: {@code group:artifact:packaging:version} (no leading tree connector, no scope)
 *   <li>child: {@code group:artifact:type[:classifier]:version:scope} preceded by tree connectors
 *       ({@code +-}, {@code \-}, {@code |}, spaces) and possibly a trailing {@code (annotation)}
 * </ul>
 *
 * <p>Failure modes handled: trailing annotations such as {@code (optional)} are stripped; scopes
 * outside {compile,runtime,test,provided} (e.g. {@code system}) are counted in
 * {@link ResolvedModule#unmappedScopes()} rather than dropped; classifier-bearing (6-token) and
 * scope-less (4-token) child lines are both accepted. The tree is flattened — depth is irrelevant to
 * a resolved-set comparison.
 */
public final class MavenDependencyTreeParser {

    /** Leading characters that make up tree connectors, ASCII and Unicode box-drawing. */
    private static final String CONNECTORS = " \t+-\\|`│├└─⬌";

    public List<ResolvedModule> parse(String treeText) {
        List<ResolvedModule> modules = new ArrayList<>();
        if (treeText == null || treeText.isBlank()) {
            return modules;
        }
        ModuleBuilder current = null;
        for (String rawLine : treeText.split("\n", -1)) {
            String line = stripTrailingCarriageReturn(rawLine);
            if (line.isBlank()) {
                continue;
            }
            if (isRootLine(line)) {
                if (current != null) {
                    modules.add(current.build());
                }
                current = ModuleBuilder.fromRoot(line.strip());
                continue;
            }
            if (current == null) {
                // A child line before any root: ignore defensively rather than fail the whole parse.
                continue;
            }
            current.addChild(coordinateToken(line));
        }
        if (current != null) {
            modules.add(current.build());
        }
        return modules;
    }

    private static boolean isRootLine(String line) {
        char first = line.charAt(0);
        return CONNECTORS.indexOf(first) < 0;
    }

    /** Strips leading tree connectors and any trailing {@code (annotation)} to leave the coordinate. */
    private static String coordinateToken(String line) {
        int start = 0;
        while (start < line.length() && CONNECTORS.indexOf(line.charAt(start)) >= 0) {
            start++;
        }
        String remainder = line.substring(start).strip();
        int annotation = remainder.indexOf(" (");
        if (annotation >= 0) {
            remainder = remainder.substring(0, annotation).strip();
        }
        return remainder;
    }

    private static String stripTrailingCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static final class ModuleBuilder {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String packaging;
        private final Map<VerifyScope, List<ResolvedArtifact>> scopes = new EnumMap<>(VerifyScope.class);
        private final Map<String, Integer> unmapped = new TreeMap<>();

        private ModuleBuilder(String groupId, String artifactId, String version, String packaging) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.packaging = packaging;
        }

        static ModuleBuilder fromRoot(String coordinate) {
            String[] parts = coordinate.split(":");
            String group = part(parts, 0);
            String artifact = part(parts, 1);
            String packaging = parts.length >= 3 ? parts[2] : "jar";
            String version = parts.length >= 3 ? parts[parts.length - 1] : "";
            return new ModuleBuilder(group, artifact, version, packaging);
        }

        void addChild(String coordinate) {
            if (coordinate.isBlank()) {
                return;
            }
            String[] parts = coordinate.split(":");
            // group:artifact:type:version:scope (5) or group:artifact:type:classifier:version:scope (6)
            // or the rare scope-less group:artifact:type:version (4).
            String group = part(parts, 0);
            String artifact = part(parts, 1);
            String type = part(parts, 2);
            String classifier;
            String version;
            String scopeToken;
            switch (parts.length) {
                case 6 -> {
                    classifier = parts[3];
                    version = parts[4];
                    scopeToken = parts[5];
                }
                case 5 -> {
                    classifier = "";
                    version = parts[3];
                    scopeToken = parts[4];
                }
                case 4 -> {
                    classifier = "";
                    version = parts[3];
                    scopeToken = "compile";
                }
                default -> {
                    return;
                }
            }
            Optional<VerifyScope> scope = VerifyScope.fromMavenToken(scopeToken);
            if (scope.isEmpty()) {
                unmapped.merge(scopeToken.trim().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
                return;
            }
            ResolvedArtifact artifactRecord = new ResolvedArtifact(group, artifact, type, classifier, version);
            scopes.computeIfAbsent(scope.get(), key -> new ArrayList<>()).add(artifactRecord);
        }

        ResolvedModule build() {
            return new ResolvedModule(groupId, artifactId, version, packaging, scopes, unmapped);
        }

        private static String part(String[] parts, int index) {
            return index < parts.length ? parts[index] : "";
        }
    }
}
