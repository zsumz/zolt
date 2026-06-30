package com.zolt.build.clean;

import java.nio.file.Path;
import java.util.List;

public record CleanResult(List<Path> deletedPaths) {
    public CleanResult {
        deletedPaths = List.copyOf(deletedPaths);
    }

    public int deletedCount() {
        return deletedPaths.size();
    }
}
