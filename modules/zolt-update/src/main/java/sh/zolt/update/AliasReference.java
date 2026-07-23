package sh.zolt.update;

import java.util.Optional;

/**
 * A single place a {@code [versions]} alias is referenced: a stable display label (e.g.
 * {@code [dependencies].com.google.guava:guava}) and, when the reference is a concrete coordinate,
 * its {@code group:artifact} so the alias's update candidates can be discovered.
 */
public record AliasReference(String label, Optional<String> coordinate) {
    public AliasReference {
        coordinate = coordinate == null ? Optional.empty() : coordinate;
    }
}
