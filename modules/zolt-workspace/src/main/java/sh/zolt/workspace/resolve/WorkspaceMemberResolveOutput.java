package sh.zolt.workspace.resolve;

import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.ZoltLockfile;
import java.util.Set;

record WorkspaceMemberResolveOutput(
        String member,
        ZoltLockfile lockfile,
        Set<PackageId> exportedPackageIds) {
}
