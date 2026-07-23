package sh.zolt.explain.maven;

import sh.zolt.explain.ExplainSignal;
import sh.zolt.explain.ExplainSignals;
import java.util.ArrayList;
import java.util.List;

/**
 * Derives {@code maven.plugin.exec-*} signals from an exec-shaped plugin's statically extracted
 * invocations. A single-command invocation is a {@code exec-mappable} WARN (Zolt can draft a
 * {@code kind = "exec"} step but cannot infer its inputs/outputs); an invocation carrying shell
 * metacharacters or an antrun control-flow target is an {@code exec-unmappable} BLOCK. Node
 * provisioning ({@code install-node-and-*}) is an honest downgrade — Zolt probes PATH rather than
 * provisioning a toolchain — so it stays a WARN with a provisioning-specific next step.
 */
final class MavenExecPluginSignals {
    private MavenExecPluginSignals() {
    }

    static List<ExplainSignal> signals(String project, MavenPluginInspection plugin) {
        List<ExplainSignal> signals = new ArrayList<>();
        for (MavenExecInvocation invocation : plugin.execInvocations()) {
            signals.add(signal(project, plugin.coordinate(), invocation));
        }
        return signals;
    }

    private static ExplainSignal signal(String project, String coordinate, MavenExecInvocation invocation) {
        if (isNodeProvisioning(invocation)) {
            return ExplainSignals.MAVEN_PLUGIN_EXEC_MAPPABLE.signal(
                    project,
                    "Plugin `" + coordinate + "` provisions Node via `" + invocation.goal()
                            + "`; Zolt has no node-provisioning step and probes Node/npm on PATH instead.",
                    "Provision Node/npm in CI or via asdf so the process exec tool can probe it on PATH;"
                            + " no exec step is drafted for this goal.");
        }
        if (!invocation.mappable()) {
            return ExplainSignals.MAVEN_PLUGIN_EXEC_UNMAPPABLE.signal(project, unmappableMessage(coordinate, invocation));
        }
        return ExplainSignals.MAVEN_PLUGIN_EXEC_MAPPABLE.signal(project, mappableMessage(coordinate, invocation));
    }

    private static String mappableMessage(String coordinate, MavenExecInvocation invocation) {
        return "Plugin `" + coordinate + "` statically maps to an exec step (" + describe(invocation)
                + phaseSuffix(invocation) + "); declare its inputs and output to complete the draft.";
    }

    private static String unmappableMessage(String coordinate, MavenExecInvocation invocation) {
        if (invocation.shellUnsafe()) {
            return "Plugin `" + coordinate + "` passes shell metacharacters to " + command(invocation)
                    + ", which Zolt's argv-array exec surface cannot express.";
        }
        return "Plugin `" + coordinate + "` runs a maven-antrun target with control flow or multiple tasks,"
                + " outside the single-command exec surface.";
    }

    private static String describe(MavenExecInvocation invocation) {
        if (invocation.mainClass().isPresent()) {
            return "exec:java main class " + command(invocation);
        }
        if (invocation.executable().isPresent()) {
            return "process command " + command(invocation);
        }
        return "an exec command";
    }

    private static String command(MavenExecInvocation invocation) {
        if (invocation.mainClass().isPresent()) {
            return "`" + invocation.mainClass().orElseThrow() + "`";
        }
        if (invocation.executable().isPresent()) {
            return "`" + invocation.executable().orElseThrow() + "`";
        }
        return "the configured command";
    }

    private static String phaseSuffix(MavenExecInvocation invocation) {
        return invocation.phase().map(phase -> " in phase `" + phase + "`").orElse("");
    }

    private static boolean isNodeProvisioning(MavenExecInvocation invocation) {
        return invocation.goal().startsWith("install-node");
    }
}
