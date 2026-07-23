package sh.zolt.maven.metadata;

import java.util.List;

/**
 * The parsed contents of a {@code maven-metadata.xml} version listing. Only the version list is
 * retained; the mutable {@code <latest>}/{@code <release>} hints are ignored — version selection is
 * driven entirely by Zolt's own comparator and stability rules.
 */
public record MavenMetadata(List<String> versions) {
    public MavenMetadata {
        versions = versions == null ? List.of() : List.copyOf(versions);
    }
}
