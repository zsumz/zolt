package com.zolt.classpath;

import java.nio.file.Path;
import java.util.List;

public record Classpath(List<Path> entries) {
    public Classpath {
        entries = List.copyOf(entries);
    }
}
