package sh.zolt.explain.verify;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One parsed line of a Gradle {@code dependencies} tree: a resolved module coordinate or a
 * {@code project :x} reference, at a tree depth, optionally carrying a {@code (c)}/{@code (*)}/{@code (n)}
 * marker. {@link GradleDependencyTreeParser} builds these per configuration section and turns them into
 * {@link ResolvedArtifact}s.
 */
final class GradleTreeNode {

    /** Leading characters that make up Gradle tree connectors ({@code +--- }, {@code \--- }, {@code |}). */
    private static final String CONNECTORS = " |+\\-";

    enum Kind {
        MODULE,
        PROJECT
    }

    private final int depth;
    private final Kind kind;
    private final String group;
    private final String artifact;
    private final String version;
    private final String projectPath;
    private final String marker;

    private GradleTreeNode(
            int depth, Kind kind, String group, String artifact, String version, String projectPath, String marker) {
        this.depth = depth;
        this.kind = kind;
        this.group = group;
        this.artifact = artifact;
        this.version = version;
        this.projectPath = projectPath;
        this.marker = marker;
    }

    /**
     * Parses one tree line into a node, or returns {@code null} for lines that are not resolvable tree
     * entries (blank connectors, legend lines, malformed coordinates). Depth is the connector prefix
     * length divided by five (Gradle indents each level by a fixed five-character block).
     */
    static GradleTreeNode parse(String line) {
        int start = 0;
        while (start < line.length() && CONNECTORS.indexOf(line.charAt(start)) >= 0) {
            start++;
        }
        if (start == 0 || start % 5 != 0) {
            return null;
        }
        int depth = start / 5;
        String remainder = line.substring(start).strip();
        if (remainder.isEmpty()) {
            return null;
        }
        String marker = null;
        for (String candidate : List.of("(c)", "(*)", "(n)")) {
            if (remainder.endsWith(" " + candidate)) {
                marker = candidate;
                remainder = remainder.substring(0, remainder.length() - candidate.length() - 1).strip();
                break;
            }
        }
        if (remainder.startsWith("project ")) {
            String path = remainder.substring("project ".length()).strip();
            return new GradleTreeNode(depth, Kind.PROJECT, "", "", "", path.startsWith(":") ? path : ":" + path, marker);
        }
        int arrow = remainder.lastIndexOf(" -> ");
        String resolvedVersion = arrow >= 0 ? remainder.substring(arrow + 4).strip() : null;
        String left = arrow >= 0 ? remainder.substring(0, arrow).strip() : remainder;
        String[] parts = left.split(":");
        if (parts.length < 2) {
            return null;
        }
        String version = resolvedVersion != null ? resolvedVersion : (parts.length >= 3 ? parts[2] : "");
        return new GradleTreeNode(depth, Kind.MODULE, parts[0], parts[1], version, null, marker);
    }

    String marker() {
        return marker;
    }

    ResolvedArtifact toArtifact(Map<String, GradleProjectCoordinates> projects) {
        if (kind == Kind.PROJECT) {
            GradleProjectCoordinates coordinates = projects.get(projectPath);
            if (coordinates == null) {
                return null;
            }
            return new ResolvedArtifact(coordinates.group(), coordinates.artifact(), "jar", "", coordinates.version());
        }
        return new ResolvedArtifact(group, artifact, "jar", "", version);
    }

    /**
     * Identities of BOM/platform nodes within a section: a node whose direct children are all
     * {@code (c)} constraints contributes only constraints, not a jar, so it is excluded from the
     * resolved set (mirroring Maven's {@code dependency:tree}, which omits imported BOMs).
     */
    static Set<String> platformKeys(List<GradleTreeNode> nodes) {
        Set<String> platforms = new HashSet<>();
        for (int index = 0; index < nodes.size(); index++) {
            GradleTreeNode node = nodes.get(index);
            if (node.kind != Kind.MODULE) {
                continue;
            }
            boolean sawChild = false;
            boolean allConstraints = true;
            for (int next = index + 1; next < nodes.size() && nodes.get(next).depth > node.depth; next++) {
                if (nodes.get(next).depth == node.depth + 1) {
                    sawChild = true;
                    if (!"(c)".equals(nodes.get(next).marker)) {
                        allConstraints = false;
                        break;
                    }
                }
            }
            if (sawChild && allConstraints) {
                platforms.add(node.group + ":" + node.artifact);
            }
        }
        return platforms;
    }
}
