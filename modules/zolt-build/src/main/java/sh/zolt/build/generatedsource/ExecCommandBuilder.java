package sh.zolt.build.generatedsource;

import sh.zolt.project.GeneratedSourceStep;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Assembles the argv for a jvm-runner exec step: {@code <java> -cp <locked jars> <mainClass> <args>}. */
final class ExecCommandBuilder {
    private final String pathSeparator;

    ExecCommandBuilder(String pathSeparator) {
        this.pathSeparator = pathSeparator;
    }

    List<String> command(Path javaExecutable, List<Path> toolClasspath, GeneratedSourceStep step) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.add("-cp");
        command.add(joinClasspath(toolClasspath));
        command.add(step.exec().tool().mainClass());
        command.addAll(step.exec().args());
        return List.copyOf(command);
    }

    private String joinClasspath(List<Path> toolClasspath) {
        return toolClasspath.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .sorted()
                .collect(Collectors.joining(pathSeparator));
    }
}
