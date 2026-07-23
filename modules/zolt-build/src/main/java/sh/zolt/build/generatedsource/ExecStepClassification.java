package sh.zolt.build.generatedsource;

import sh.zolt.project.GeneratedSourceStep;
import java.nio.file.Path;

/**
 * Classifies an exec step's build position. A step is <em>post-compile</em> when it uses the built-in
 * {@code project} pseudo-tool or declares an input under the compile output ({@code classes} /
 * {@code test-classes}); such a step runs after compilation. This predicate is the single source of
 * truth shared by the scheduler (ordering), the service (execution phase), and the module fingerprint
 * (which must NOT hash post-compile outputs, to avoid a compile-gates-its-own-input cycle).
 */
public final class ExecStepClassification {
    private ExecStepClassification() {
    }

    public static boolean isProjectRunner(GeneratedSourceStep step) {
        return "project".equals(step.exec().tool().runner());
    }

    public static boolean isPostCompile(GeneratedSourceStep step, Path projectRoot, String outputRoot) {
        if (isProjectRunner(step)) {
            return true;
        }
        Path classes = projectRoot.resolve(outputRoot).resolve("classes").normalize();
        Path testClasses = projectRoot.resolve(outputRoot).resolve("test-classes").normalize();
        for (String input : step.inputs()) {
            Path base = projectRoot.resolve(ExecInputExpander.literalBase(input)).normalize();
            if (base.startsWith(classes) || base.startsWith(testClasses)) {
                return true;
            }
        }
        return false;
    }
}
