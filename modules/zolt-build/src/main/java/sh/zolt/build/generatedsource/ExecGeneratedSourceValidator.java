package sh.zolt.build.generatedsource;

import sh.zolt.build.BuildException;
import sh.zolt.project.ExecGenerationSettings;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProducesLane;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates a single exec step just before it runs: jvm-runner sanity, lane/produces consistency for
 * the scope it is declared in, and real-path-contained, existing inputs.
 */
final class ExecGeneratedSourceValidator {
    private ExecGeneratedSourceValidator() {
    }

    static void validateStep(Path projectRoot, String scope, GeneratedSourceStep step) {
        ExecGenerationSettings exec = step.exec();
        String subject = "[generated." + scope + "." + step.id() + "]";
        if (!"jvm".equals(exec.tool().runner())) {
            throw BuildException.actionable(
                    "Exec step " + subject + " uses runner `" + exec.tool().runner() + "`.",
                    "Stage 1 supports only the jvm runner; the process runner arrives in a later stage.");
        }
        if (exec.tool().mainClass().isBlank()) {
            throw BuildException.actionable(
                    "Exec tool `" + exec.toolName() + "` for " + subject + " has no mainClass.",
                    "Add mainClass to [generated.execTools." + exec.toolName() + "].");
        }
        if (exec.tool().coordinates().isEmpty()) {
            throw BuildException.actionable(
                    "Exec tool `" + exec.toolName() + "` for " + subject + " declares no coordinates.",
                    "Add at least one { coordinate, version|versionRef } to [generated.execTools." + exec.toolName() + "].");
        }
        validateLane(scope, step, subject);
        validateInputs(projectRoot, scope, step, subject);
    }

    private static void validateLane(String scope, GeneratedSourceStep step, String subject) {
        ProducesLane produces = step.exec().produces();
        if ("main".equals(scope) && produces == ProducesLane.TEST_SOURCES) {
            throw BuildException.actionable(
                    "Exec step " + subject + " produces test-sources but is declared in the main lane.",
                    "Move it to [generated.test." + step.id() + "] or set produces = \"java-sources\".");
        }
        if ("test".equals(scope) && produces == ProducesLane.JAVA_SOURCES) {
            throw BuildException.actionable(
                    "Exec step " + subject + " produces java-sources but is declared in the test lane.",
                    "Move it to [generated.main." + step.id() + "] or set produces = \"test-sources\".");
        }
    }

    private static void validateInputs(Path projectRoot, String scope, GeneratedSourceStep step, String subject) {
        for (String input : step.inputs()) {
            if (ExecInputExpander.isGlob(input)) {
                continue;
            }
            Path path = ExecGeneratedSourcePaths.inputPath(projectRoot, input, scope, step.id(), "inputs");
            if (!Files.exists(path)) {
                throw BuildException.actionable(
                        "Exec step " + subject + " input `" + input + "` does not exist.",
                        "Create the input or fix the path in " + subject + ".inputs.");
            }
        }
    }
}
