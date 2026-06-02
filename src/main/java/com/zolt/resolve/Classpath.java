package com.zolt.resolve;

import java.nio.file.Path;
import java.util.List;

public record Classpath(List<Path> entries) {
    public Classpath {
        entries = List.copyOf(entries);
    }
}
