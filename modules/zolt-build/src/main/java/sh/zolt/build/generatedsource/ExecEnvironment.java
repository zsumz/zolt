package sh.zolt.build.generatedsource;

import sh.zolt.project.GeneratedSourceStep;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the curated, never-inherited environment for an exec subprocess: OS essentials (PATH, HOME),
 * the step's declared literal {@code env}, then Zolt-owned {@code ZOLT_*} context. Ambient environment
 * inheritance, secretEnv, and inheritEnv are deliberately out of scope for stage 1.
 */
final class ExecEnvironment {
    private ExecEnvironment() {
    }

    static Map<String, String> build(Path projectRoot, Path output, String scope, GeneratedSourceStep step) {
        Map<String, String> environment = new LinkedHashMap<>();
        putIfPresent(environment, "PATH", System.getenv("PATH"));
        putIfPresent(environment, "HOME", System.getenv("HOME"));
        environment.putAll(step.exec().env());
        environment.put("ZOLT_PROJECT_ROOT", projectRoot.toString());
        environment.put("ZOLT_STEP_ID", step.id());
        environment.put("ZOLT_STEP_SCOPE", scope);
        environment.put("ZOLT_OUTPUT_DIR", output.toString());
        return environment;
    }

    private static void putIfPresent(Map<String, String> environment, String name, String value) {
        if (value != null && !value.isBlank()) {
            environment.put(name, value);
        }
    }
}
