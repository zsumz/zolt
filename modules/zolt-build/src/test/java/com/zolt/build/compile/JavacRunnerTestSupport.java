package com.zolt.build.compile;

import java.util.List;
import java.util.function.Consumer;

public final class JavacRunnerTestSupport {
    private JavacRunnerTestSupport() {
    }

    public static JavacRunner javacRunner(String pathSeparator, Consumer<List<String>> commandConsumer) {
        return new JavacRunner(pathSeparator, command -> {
            commandConsumer.accept(command);
            return new JavacRunner.ProcessResult(0, "");
        });
    }
}
