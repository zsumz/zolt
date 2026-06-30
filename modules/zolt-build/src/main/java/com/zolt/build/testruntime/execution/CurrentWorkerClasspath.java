package com.zolt.build.testruntime.execution;

import com.zolt.build.testruntime.TestRunException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class CurrentWorkerClasspath {
    public List<Path> discover() {
        return discover(System.getProperty("java.class.path", ""), java.io.File.pathSeparator);
    }

    List<Path> discover(String classpath, String pathSeparator) {
        List<Path> entries = Arrays.stream(classpath.split(pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        if (entries.isEmpty()) {
            throw new TestRunException(
                    "Could not determine Zolt worker classpath for test execution. "
                            + "Run zolt test from the packaged launcher or check java.class.path.");
        }
        return entries;
    }
}
