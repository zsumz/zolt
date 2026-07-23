package sh.zolt.build.generatedsource;

import sh.zolt.build.BuildException;
import sh.zolt.project.ExecGenerationSettings;
import sh.zolt.project.GeneratedSourceStep;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Builds the curated, never-inherited environment for an exec subprocess: OS essentials (PATH, HOME)
 * read from Zolt's ambient environment, the explicit {@code inheritEnv} passthrough allowlist, the
 * step's declared literal {@code env}, {@code secretEnv} indirection (a target name is filled from the
 * value of a named source variable at run time — only names ever appear here), then Zolt-owned {@code
 * ZOLT_*} context. Ambient variables outside the passthrough allowlist are never inherited.
 */
final class ExecEnvironment {
    private ExecEnvironment() {
    }

    static Map<String, String> build(
            Path projectRoot,
            Path output,
            String scope,
            GeneratedSourceStep step,
            UnaryOperator<String> ambientEnv) {
        ExecGenerationSettings exec = step.exec();
        String subject = "[generated." + scope + "." + step.id() + "]";
        Map<String, String> environment = new LinkedHashMap<>();
        putIfPresent(environment, "PATH", ambientEnv.apply("PATH"));
        putIfPresent(environment, "HOME", ambientEnv.apply("HOME"));
        for (String name : exec.inheritEnv()) {
            putIfPresent(environment, name, ambientEnv.apply(name));
        }
        environment.putAll(exec.env());
        for (Map.Entry<String, String> secret : exec.secretEnv().entrySet()) {
            String target = secret.getKey();
            String source = secret.getValue();
            String value = ambientEnv.apply(source);
            if (value == null) {
                throw BuildException.actionable(
                        "Exec step " + subject + " maps secret env `" + target + "` from `" + source
                                + "`, but `" + source + "` is not set in Zolt's environment.",
                        "Export `" + source + "` before running; its value is injected as `" + target
                                + "` at run time and never written to config, the lock, fingerprints, plans, or logs.");
            }
            environment.put(target, value);
        }
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
