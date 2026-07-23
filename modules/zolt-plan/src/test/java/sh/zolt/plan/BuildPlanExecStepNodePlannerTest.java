package sh.zolt.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.generated.GeneratedSourceEvidence;
import sh.zolt.project.ExecGenerationSettings;
import sh.zolt.project.ExecToolCoordinate;
import sh.zolt.project.ExecToolSettings;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.OpenApiGenerationSettings;
import sh.zolt.project.ProducesLane;
import sh.zolt.project.ProtobufGenerationSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildPlanExecStepNodePlannerTest {
    private static final ExecToolSettings JVM_TOOL = new ExecToolSettings(
            "jvm",
            List.of(new ExecToolCoordinate("org.jooq:jooq-codegen", Optional.of("3.19.15"), Optional.empty())),
            "org.jooq.codegen.GenerationTool");

    private final BuildPlanExecStepNodePlanner planner = new BuildPlanExecStepNodePlanner();

    @TempDir
    private Path tempDir;

    @Test
    void readyWhenToolLockedAndInputsPresent() throws IOException {
        writeInput("src/main/jooq/config.xml");
        GeneratedSourceStep step = execStep("model", "target/generated/sources/jooq",
                List.of("src/main/jooq/config.xml"), ProducesLane.JAVA_SOURCES, JVM_TOOL);

        PlanNode node = node(step, true, false, "missing");

        assertEquals("exec-step", node.kind());
        assertEquals(PlanNodeStatus.READY, node.status());
        assertTrue(node.blockers().isEmpty(), node.blockers().toString());
        assertTrue(node.details().contains("tool: tool"));
        assertTrue(node.details().contains("derivedPosition: before main compile"));
        assertTrue(node.details().contains("toolCoordinates: org.jooq:jooq-codegen:3.19.15"));
    }

    @Test
    void blocksWhenExecToolingNotLocked() throws IOException {
        writeInput("src/main/jooq/config.xml");
        GeneratedSourceStep step = execStep("model", "target/generated/sources/jooq",
                List.of("src/main/jooq/config.xml"), ProducesLane.JAVA_SOURCES, JVM_TOOL);

        PlanNode node = node(step, false, false, "missing");

        assertEquals(PlanNodeStatus.BLOCKED, node.status());
        assertBlocker(node, "exec-tool-not-locked");
    }

    @Test
    void blocksMissingInput() {
        GeneratedSourceStep step = execStep("model", "target/generated/sources/jooq",
                List.of("src/main/jooq/config.xml"), ProducesLane.JAVA_SOURCES, JVM_TOOL);

        assertBlocker(node(step, true, false, "input-missing"), "missing-exec-input");
    }

    @Test
    void blocksOutputOutsideProject() throws IOException {
        writeInput("src/main/jooq/config.xml");
        GeneratedSourceStep step = execStep("model", "../outside",
                List.of("src/main/jooq/config.xml"), ProducesLane.JAVA_SOURCES, JVM_TOOL);

        assertBlocker(node(step, true, false, "missing"), "invalid-exec-output");
    }

    @Test
    void blocksInputUnderCompiledClasses() {
        GeneratedSourceStep step = execStep("model", "target/generated/sources/jooq",
                List.of("target/classes/com/example/App.class"), ProducesLane.JAVA_SOURCES, JVM_TOOL);

        assertBlocker(node(step, true, false, "missing"), "exec-input-under-compiled-classes");
    }

    @Test
    void blocksUnresolvedTool() throws IOException {
        writeInput("src/main/jooq/config.xml");
        GeneratedSourceStep step = execStep("model", "target/generated/sources/jooq",
                List.of("src/main/jooq/config.xml"), ProducesLane.JAVA_SOURCES, ExecToolSettings.empty());

        assertBlocker(node(step, true, false, "missing"), "unresolved-exec-tool");
    }

    @Test
    void blocksOrderingCycle() {
        GeneratedSourceStep a = execStep("a", "target/generated/a",
                List.of("target/generated/b/out.txt"), ProducesLane.JAVA_SOURCES, JVM_TOOL);
        GeneratedSourceStep b = execStep("b", "target/generated/b",
                List.of("target/generated/a/out.txt"), ProducesLane.JAVA_SOURCES, JVM_TOOL);

        List<PlanNode> nodes = planner.nodes(
                tempDir.normalize(),
                List.of(evidence("main", a, false, "missing"), evidence("main", b, false, "missing")),
                "main",
                "target",
                true);

        assertEquals(2, nodes.size());
        nodes.forEach(node -> assertBlocker(node, "exec-ordering-cycle"));
    }

    private PlanNode node(GeneratedSourceStep step, boolean toolLocked, boolean outputExists, String freshness) {
        List<PlanNode> nodes = planner.nodes(
                tempDir.normalize(),
                List.of(evidence("main", step, outputExists, freshness)),
                "main",
                "target",
                toolLocked);
        assertEquals(1, nodes.size());
        return nodes.getFirst();
    }

    private GeneratedSourceEvidence evidence(String scope, GeneratedSourceStep step, boolean outputExists, String freshness) {
        List<Path> inputs = step.inputs().stream().map(input -> tempDir.resolve(input).normalize()).toList();
        boolean inputsPresent = inputs.stream().allMatch(Files::exists);
        return new GeneratedSourceEvidence(
                step.id(),
                "generated." + scope + "." + step.id(),
                scope,
                step,
                tempDir.resolve(step.output()).normalize(),
                inputs,
                outputExists,
                inputsPresent,
                freshness,
                "zolt-owned-clean",
                scope + "-compile",
                "",
                "",
                "");
    }

    private void writeInput(String path) throws IOException {
        Path input = tempDir.resolve(path);
        Files.createDirectories(input.getParent());
        Files.writeString(input, "<configuration/>\n");
    }

    private static GeneratedSourceStep execStep(
            String id,
            String output,
            List<String> inputs,
            ProducesLane produces,
            ExecToolSettings tool) {
        ExecGenerationSettings exec = new ExecGenerationSettings(
                "tool", tool, List.of(), produces, Optional.empty(), Map.of(), "content");
        return new GeneratedSourceStep(
                id,
                GeneratedSourceKind.EXEC,
                "java",
                output,
                inputs,
                true,
                true,
                OpenApiGenerationSettings.empty(),
                ProtobufGenerationSettings.empty(),
                exec);
    }

    private static void assertBlocker(PlanNode node, String code) {
        assertTrue(
                node.blockers().stream().anyMatch(blocker -> blocker.code().equals(code)),
                "Expected blocker " + code + " in " + node.blockers());
    }
}
