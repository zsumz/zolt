package sh.zolt.build.generatedsource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles the argv for an exec step. The {@code jvm} and {@code project} runners launch
 * {@code <java> -cp <classpath> <mainClass> <args>} (the jvm runner over locked tool jars, the project
 * runner over the member's compiled classes plus runtime classpath); the {@code process} runner
 * launches the resolved binary directly with its args.
 */
final class ExecCommandBuilder {
    private final String pathSeparator;

    ExecCommandBuilder(String pathSeparator) {
        this.pathSeparator = pathSeparator;
    }

    /** jvm runner: the tool classpath is sorted for a stable command; entries are locked tool jars. */
    List<String> jvmCommand(Path javaExecutable, List<Path> toolClasspath, String mainClass, List<String> args) {
        return javaCommand(javaExecutable, joinSorted(toolClasspath), mainClass, args);
    }

    /** project runner: classpath order is preserved (compiled classes first, then runtime deps). */
    List<String> projectCommand(Path javaExecutable, List<Path> classpath, String mainClass, List<String> args) {
        return javaCommand(javaExecutable, joinOrdered(classpath), mainClass, args);
    }

    List<String> processCommand(Path binary, List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(binary.toString());
        command.addAll(args);
        return List.copyOf(command);
    }

    private static List<String> javaCommand(Path javaExecutable, String classpath, String mainClass, List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass);
        command.addAll(args);
        return List.copyOf(command);
    }

    private String joinSorted(List<Path> classpath) {
        return classpath.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .sorted()
                .collect(Collectors.joining(pathSeparator));
    }

    private String joinOrdered(List<Path> classpath) {
        return classpath.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .collect(Collectors.joining(pathSeparator));
    }
}
