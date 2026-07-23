package sh.zolt.project;

import java.util.List;

/**
 * The parsed {@code [bom]} section of a BOM member's zolt.toml. Present only when
 * {@link PackageSettings#mode()} is {@link PackageMode#BOM}; otherwise {@link #none()}.
 *
 * <p>A BOM publishes a curated version set as a Maven {@code <dependencyManagement>} POM. It mirrors
 * the consuming vocabulary: {@link #members} is the workspace family (emitted at their locked GAV
 * versions), {@link #versions} mirrors {@code [versions]} (fixed third-party pins), and
 * {@link #imports} mirrors {@code [platforms]} (import-scope BOM composition). All versions are fixed
 * literals; ranges, dynamic selectors, and interpolation are rejected at parse time.
 */
public record BomSettings(Members members, List<ManagedVersion> versions, List<ImportedBom> imports) {
    public BomSettings {
        members = members == null ? Members.none() : members;
        versions = versions == null ? List.of() : List.copyOf(versions);
        imports = imports == null ? List.of() : List.copyOf(imports);
    }

    public static BomSettings none() {
        return new BomSettings(Members.none(), List.of(), List.of());
    }

    /** True when the BOM declares a workspace family (either {@code members = true} or an explicit path list). */
    public boolean hasMembers() {
        return members.declared();
    }

    /**
     * The workspace family selection. {@link #all()} is {@code members = true} (every enclosing
     * workspace member); {@link #paths()} is an explicit member-path list; {@link #exclude()} removes
     * paths from an otherwise-selected family. A standalone (non-workspace) BOM declares none of these.
     */
    public record Members(boolean all, List<String> paths, List<String> exclude) {
        public Members {
            paths = paths == null ? List.of() : List.copyOf(paths);
            exclude = exclude == null ? List.of() : List.copyOf(exclude);
        }

        public static Members none() {
            return new Members(false, List.of(), List.of());
        }

        public boolean declared() {
            return all || !paths.isEmpty();
        }
    }

    /**
     * One {@code [bom.versions]} entry: a curated third-party pin. {@link #version()} is the resolved
     * fixed literal (a {@code versionRef} is resolved against {@code [versions]} at parse time and
     * retained in {@link #versionRef()} for round-trip fidelity). Optional classifier/type ride into
     * the emitted {@code <dependencyManagement>} entry in Maven element order.
     */
    public record ManagedVersion(String coordinate, String version, String versionRef, String classifier, String type) {
        public ManagedVersion {
            versionRef = blankToNull(versionRef);
            classifier = blankToNull(classifier);
            type = blankToNull(type);
        }
    }

    /**
     * One {@code [bom.imports]} entry: another BOM composed by import scope, emitted as
     * {@code <type>pom</type><scope>import</scope>}. {@link #version()} is the resolved fixed literal.
     */
    public record ImportedBom(String coordinate, String version, String versionRef) {
        public ImportedBom {
            versionRef = blankToNull(versionRef);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
