package com.zolt.workspace.resolve;

import com.zolt.dependency.PackageId;
import com.zolt.lockfile.ZoltLockfile;
import java.util.Set;

record WorkspaceMemberResolveOutput(
        String member,
        ZoltLockfile lockfile,
        Set<PackageId> exportedPackageIds) {
}
