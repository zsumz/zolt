package sh.zolt.update;

import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.ProjectConfig;
import java.util.Optional;

/**
 * One project unit to report on: a display label, its parsed configuration, and its lockfile when
 * present (used to show effective versions of platform-managed dependencies). A single project is
 * one scope; a workspace is one scope per member (plus the root when it declares surfaces).
 */
public record OutdatedScope(String label, ProjectConfig config, Optional<ZoltLockfile> lockfile) {
    public OutdatedScope {
        lockfile = lockfile == null ? Optional.empty() : lockfile;
    }

    public static OutdatedScope of(String label, ProjectConfig config) {
        return new OutdatedScope(label, config, Optional.empty());
    }
}
