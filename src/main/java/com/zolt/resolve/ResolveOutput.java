package com.zolt.resolve;

import com.zolt.lockfile.ZoltLockfile;

public record ResolveOutput(
        ZoltLockfile lockfile,
        int downloadCount) {
}
