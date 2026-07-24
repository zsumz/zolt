package sh.zolt.explain.emit;

import sh.zolt.explain.maven.MavenExecInvocation;
import sh.zolt.explain.maven.MavenPluginInspection;
import sh.zolt.project.ExecGenerationSettings;
import sh.zolt.project.ExecToolCoordinate;
import sh.zolt.project.ExecToolSettings;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.OpenApiGenerationSettings;
import sh.zolt.project.ProducesLane;
import sh.zolt.project.ProtobufGenerationSettings;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Drafts {@code kind = "exec"} steps from statically extracted exec-shaped Maven plugin invocations.
 *
 * <p>The draft is deliberately incomplete and says so: inputs are a {@code REPLACE_ME} placeholder and
 * the output is a Zolt-convention directory, because a static audit cannot see a tool's real input
 * closure or owned output. exec:java maps to the {@code project} pseudo-tool (or a jvm exec tool when
 * {@code <executableDependencies>} pin coordinates); exec:exec, frontend, and antrun map to a
 * {@code process} tool that probes PATH. Every downgrade (a project tool cannot regenerate compile-lane
 * sources, a jvm tool with no static version, a skipped unmappable or node-provisioning invocation) is
 * recorded as a review note rather than emitted as a silently wrong step.
 */
final class MavenExecStepDrafter {
    static final String INPUT_PLACEHOLDER = "REPLACE_ME";

    private MavenExecStepDrafter() {
    }

    record Drafted(List<GeneratedSourceStep> mainSteps, List<GeneratedSourceStep> testSteps) {
        boolean isEmpty() {
            return mainSteps.isEmpty() && testSteps.isEmpty();
        }
    }

    static Drafted draft(List<MavenPluginInspection> plugins, List<String> notes) {
        List<GeneratedSourceStep> mainSteps = new ArrayList<>();
        List<GeneratedSourceStep> testSteps = new ArrayList<>();
        Set<String> usedIds = new LinkedHashSet<>();
        for (MavenPluginInspection plugin : plugins) {
            if (plugin.pluginManagement()) {
                continue;
            }
            for (MavenExecInvocation invocation : plugin.execInvocations()) {
                draftInvocation(plugin.coordinate(), invocation, usedIds, mainSteps, testSteps, notes);
            }
        }
        return new Drafted(List.copyOf(mainSteps), List.copyOf(testSteps));
    }

    private static void draftInvocation(
            String coordinate,
            MavenExecInvocation invocation,
            Set<String> usedIds,
            List<GeneratedSourceStep> mainSteps,
            List<GeneratedSourceStep> testSteps,
            List<String> notes) {
        String kind = kind(coordinate);
        if (invocation.goal().startsWith("install-node")) {
            notes.add("Maven plugin `" + coordinate + "` provisions Node via `" + invocation.goal()
                    + "`; no exec step was drafted because Zolt probes Node/npm on PATH. Provision it in CI or via asdf.");
            return;
        }
        if (!invocation.mappable()) {
            notes.add("Maven plugin `" + coordinate + "` invocation `" + describe(invocation)
                    + "` uses a shell or antrun control flow that the argv-array exec surface cannot express;"
                    + " no exec step was drafted. Keep it in [commands.tasks] or CI.");
            return;
        }
        Optional<ToolDraft> tool = toolFor(kind, coordinate, invocation, notes);
        if (tool.isEmpty()) {
            return;
        }
        String id = uniqueId(baseId(invocation, kind), usedIds);
        ProducesLane lane = laneFor(kind, invocation, tool.orElseThrow().projectTool(), id, notes);
        boolean test = lane == ProducesLane.TEST_SOURCES || lane == ProducesLane.TEST_RESOURCES;
        GeneratedSourceStep step = step(id, tool.orElseThrow(), invocation, lane);
        (test ? testSteps : mainSteps).add(step);
    }

    private static GeneratedSourceStep step(
            String id, ToolDraft tool, MavenExecInvocation invocation, ProducesLane lane) {
        ExecGenerationSettings exec = new ExecGenerationSettings(
                tool.toolName(),
                tool.tool(),
                invocation.arguments(),
                lane,
                Optional.empty(),
                invocation.environmentVariables(),
                "content",
                invocation.workingDirectory(),
                java.util.Map.of(),
                List.of(),
                ExecGenerationSettings.DEFAULT_TIMEOUT_SECONDS,
                Optional.empty());
        return new GeneratedSourceStep(
                id,
                GeneratedSourceKind.EXEC,
                "java",
                output(id, lane),
                List.of(INPUT_PLACEHOLDER),
                true,
                true,
                OpenApiGenerationSettings.empty(),
                ProtobufGenerationSettings.empty(),
                exec);
    }

    private record ToolDraft(String toolName, ExecToolSettings tool, boolean projectTool) {
    }

    private static Optional<ToolDraft> toolFor(
            String kind, String coordinate, MavenExecInvocation invocation, List<String> notes) {
        if ("exec".equals(kind) && invocation.mainClass().isPresent()) {
            return jvmOrProjectTool(coordinate, invocation, notes);
        }
        if (invocation.executable().isPresent()) {
            String binary = invocation.executable().orElseThrow();
            String toolName = binaryToolName(binary);
            ExecToolSettings tool = ExecToolSettings.process(
                    binary, List.of(binary, "--version"), Optional.empty(), true);
            return Optional.of(new ToolDraft(toolName, tool, false));
        }
        notes.add("Maven plugin `" + coordinate + "` declared an exec invocation without a main class or"
                + " executable Zolt could read statically; no exec step was drafted.");
        return Optional.empty();
    }

    private static Optional<ToolDraft> jvmOrProjectTool(
            String coordinate, MavenExecInvocation invocation, List<String> notes) {
        String mainClass = invocation.mainClass().orElseThrow();
        if (invocation.executableDependencies().isEmpty()) {
            return Optional.of(new ToolDraft("project", ExecToolSettings.project(mainClass), true));
        }
        List<ExecToolCoordinate> coordinates = new ArrayList<>();
        for (String dependency : invocation.executableDependencies()) {
            coordinates.add(new ExecToolCoordinate(dependency, Optional.of(INPUT_PLACEHOLDER), Optional.empty()));
        }
        notes.add("Maven plugin `" + coordinate + "` pins its exec tool via <executableDependencies>; the"
                + " drafted [generated.execTools] coordinates use a " + INPUT_PLACEHOLDER
                + " version because the audit could not read one. Set the real versions before resolving.");
        String toolName = binaryToolName(coordinate.substring(coordinate.indexOf(':') + 1));
        return Optional.of(new ToolDraft(toolName, new ExecToolSettings("jvm", coordinates, mainClass), false));
    }

    private static ProducesLane laneFor(
            String kind, MavenExecInvocation invocation, boolean projectTool, String id, List<String> notes) {
        ProducesLane lane = invocation.phase()
                .flatMap(MavenExecStepDrafter::laneFromPhase)
                .orElseGet(() -> defaultLane(kind, invocation));
        if (projectTool && lane == ProducesLane.JAVA_SOURCES) {
            notes.add("Exec step `" + id + "` runs exec:java on the project classpath (tool = \"project\"),"
                    + " which Zolt schedules after compile, so it was drafted as resources rather than"
                    + " java-sources. To generate main sources, pin the tool with explicit jvm coordinates.");
            return ProducesLane.RESOURCES;
        }
        if (projectTool && lane == ProducesLane.TEST_SOURCES) {
            notes.add("Exec step `" + id + "` runs exec:java on the project classpath (tool = \"project\"),"
                    + " scheduled after compile, so it was drafted as test-resources rather than test-sources.");
            return ProducesLane.TEST_RESOURCES;
        }
        return lane;
    }

    private static ProducesLane defaultLane(String kind, MavenExecInvocation invocation) {
        if ("exec".equals(kind) && invocation.mainClass().isPresent()) {
            return ProducesLane.JAVA_SOURCES;
        }
        if (invocation.arguments().stream().anyMatch(argument -> argument.equals("install") || argument.equals("ci"))) {
            return ProducesLane.INTERMEDIATE;
        }
        return ProducesLane.RESOURCES;
    }

    private static Optional<ProducesLane> laneFromPhase(String phase) {
        return switch (phase) {
            case "generate-sources", "process-sources", "generate" -> Optional.of(ProducesLane.JAVA_SOURCES);
            case "generate-test-sources", "process-test-sources" -> Optional.of(ProducesLane.TEST_SOURCES);
            case "generate-test-resources", "process-test-resources" -> Optional.of(ProducesLane.TEST_RESOURCES);
            case "generate-resources", "process-resources", "prepare-package", "package" ->
                    Optional.of(ProducesLane.RESOURCES);
            default -> Optional.empty();
        };
    }

    private static String output(String id, ProducesLane lane) {
        return switch (lane) {
            case JAVA_SOURCES, TEST_SOURCES -> "target/generated/sources/" + id;
            case RESOURCES, TEST_RESOURCES -> "target/generated/resources/" + id;
            case INTERMEDIATE -> "target/generated/" + id;
        };
    }

    private static String kind(String coordinate) {
        String lower = coordinate.toLowerCase();
        if (lower.contains(":exec-maven-plugin")) {
            return "exec";
        }
        if (lower.contains(":frontend-maven-plugin")) {
            return "frontend";
        }
        return "antrun";
    }

    private static String describe(MavenExecInvocation invocation) {
        return invocation.mainClass()
                .or(invocation::executable)
                .orElse(invocation.goal());
    }

    private static String baseId(MavenExecInvocation invocation, String kind) {
        String raw = invocation.executionId();
        if (raw.isBlank()) {
            raw = invocation.goal();
        }
        if (raw.isBlank()) {
            raw = kind;
        }
        String id = raw.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return id.isBlank() ? "exec" : id;
    }

    private static String uniqueId(String base, Set<String> usedIds) {
        String candidate = base;
        int suffix = 2;
        while (!usedIds.add(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String binaryToolName(String executable) {
        String base = executable;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.indexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll("[^A-Za-z0-9_-]", "-").replaceAll("^-+|-+$", "");
        return base.isBlank() ? "tool" : base;
    }
}
