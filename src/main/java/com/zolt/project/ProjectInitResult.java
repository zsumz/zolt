package com.zolt.project;

import java.nio.file.Path;

public record ProjectInitResult(
        Path projectDirectory,
        Path configFile,
        Path mainSource,
        Path testSource) {
}
