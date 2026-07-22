package sh.zolt.explain.maven;

import sh.zolt.maven.Coordinate;
import java.util.List;

/**
 * Fetches an external Maven parent chain (and any import-scoped BOMs it references) so the migration
 * audit can recover inherited properties and dependencyManagement when {@code --resolve-external-parents}
 * is set.
 *
 * <p>Implementations must never throw for an unreachable, SNAPSHOT, or otherwise unfetchable parent: they
 * return {@link RecoveredParentMetadata#unresolved(String)} with an honest review note so the audit
 * degrades to its offline behaviour instead of crashing. Determinism holds because recovered versions are
 * fixed literals; anything dynamic surfaces as a review item.
 *
 * @param repositoryUrls HTTPS repositories declared in the inspected POM chain, tried before Maven Central.
 */
public interface MavenExternalParentResolver {
    RecoveredParentMetadata resolve(Coordinate externalParent, List<String> repositoryUrls);
}
