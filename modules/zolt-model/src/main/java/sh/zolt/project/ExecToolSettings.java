package sh.zolt.project;

import java.util.List;

/**
 * A named exec tool declared under {@code [generated.execTools.<name>]}. Stage 1 supports only the
 * {@code jvm} runner: its {@code coordinates} resolve into the tool-exec scope (locked, never on app
 * classpaths) and {@code mainClass} is launched with {@code <managed-java> -cp <locked jars>}.
 */
public record ExecToolSettings(
        String runner,
        List<ExecToolCoordinate> coordinates,
        String mainClass) {
    public ExecToolSettings {
        runner = runner == null ? "" : runner;
        coordinates = coordinates == null ? List.of() : List.copyOf(coordinates);
        mainClass = mainClass == null ? "" : mainClass;
    }

    public static ExecToolSettings empty() {
        return new ExecToolSettings("", List.of(), "");
    }
}
