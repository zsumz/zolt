package sh.zolt.publish;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Inter-member completeness: a member's POM must never reference a sibling coordinate consumers
 * cannot resolve. This pure helper reports, for a member's projected publish lock, the
 * workspace-provided (inter-member) sibling coordinates that are absent from the publish set.
 *
 * <p>The workspace publish orchestrator turns a non-empty result into a Phase-1 blocker (multi-member
 * publish) or a warning (single-member publish of a workspace member renders correctly but the
 * sibling must be published at the same version).
 */
public final class PublishInterMemberGuard {
    private PublishInterMemberGuard() {
    }

    /**
     * @param memberProjectedLock the member's single-project-shaped projected lock
     * @param publishSetCoordinates {@code group:artifact} of every member in the publish set
     * @return the {@code group:artifact} of each inter-member sibling dependency absent from the set,
     *     in declaration order, de-duplicated
     */
    public static List<String> missingSiblings(ZoltLockfile memberProjectedLock, Set<String> publishSetCoordinates) {
        List<String> missing = new ArrayList<>();
        for (LockPackage lockPackage : memberProjectedLock.packages()) {
            if (lockPackage.workspace().isEmpty() || !lockPackage.direct() || !publishedScope(lockPackage.scope())) {
                continue;
            }
            String coordinate = lockPackage.packageId().groupId() + ":" + lockPackage.packageId().artifactId();
            if (!publishSetCoordinates.contains(coordinate) && !missing.contains(coordinate)) {
                missing.add(coordinate);
            }
        }
        return List.copyOf(missing);
    }

    private static boolean publishedScope(DependencyScope scope) {
        return scope == DependencyScope.COMPILE
                || scope == DependencyScope.RUNTIME
                || scope == DependencyScope.PROVIDED;
    }
}
