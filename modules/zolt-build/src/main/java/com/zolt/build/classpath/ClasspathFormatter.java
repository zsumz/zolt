package com.zolt.build.classpath;

import com.zolt.classpath.Classpath;
import java.nio.file.Path;
import java.util.StringJoiner;

public final class ClasspathFormatter {
    private final String pathSeparator;

    public ClasspathFormatter() {
        this(java.io.File.pathSeparator);
    }

    ClasspathFormatter(String pathSeparator) {
        this.pathSeparator = pathSeparator;
    }

    public String format(Classpath classpath) {
        StringJoiner joiner = new StringJoiner(pathSeparator);
        for (Path entry : classpath.entries()) {
            joiner.add(entry.toString());
        }
        return joiner + System.lineSeparator();
    }
}
