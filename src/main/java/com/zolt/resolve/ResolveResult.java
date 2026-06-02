package com.zolt.resolve;

import java.nio.file.Path;

public record ResolveResult(
        int resolvedCount,
        int downloadCount,
        int conflictCount,
        Path lockfilePath) {
}
