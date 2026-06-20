package com.zolt.build;

import java.nio.file.Path;

public record JavacResult(
        int sourceCount,
        Path outputDirectory,
        String output) {
}
