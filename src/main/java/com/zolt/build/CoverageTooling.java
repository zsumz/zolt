package com.zolt.build;

import java.nio.file.Path;
import java.util.List;

public record CoverageTooling(
        Path agentJar,
        List<Path> cliClasspath) {
    public CoverageTooling {
        cliClasspath = List.copyOf(cliClasspath);
    }
}
