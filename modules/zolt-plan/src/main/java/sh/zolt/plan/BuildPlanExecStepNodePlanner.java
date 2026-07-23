package sh.zolt.plan;

import sh.zolt.generated.GeneratedSourceEvidence;
import sh.zolt.project.ExecGenerationSettings;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProducesLane;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces a typed plan node per exec step. Position is derived from the produces lane, and blockers
 * cover every stage-1 failure: an unresolved tool ref, exec tooling absent from the lock, a missing or
 * out-of-tree input, an out-of-tree output, an input under compiled classes, and ordering cycles.
 */
final class BuildPlanExecStepNodePlanner {
    List<PlanNode> nodes(
            Path root,
            List<GeneratedSourceEvidence> generatedSources,
            String scope,
            String outputRoot,
            boolean toolLocked) {
        List<GeneratedSourceEvidence> execEvidence = generatedSources.stream()
                .filter(evidence -> evidence.scope().equals(scope))
                .filter(evidence -> evidence.step().kind() == GeneratedSourceKind.EXEC)
                .toList();
        if (execEvidence.isEmpty()) {
            return List.of();
        }
        Set<String> cyclicIds = cyclicStepIds(root, execEvidence.stream().map(GeneratedSourceEvidence::step).toList());
        Path classesRoot = root.resolve(outputRoot).resolve("classes").normalize();
        List<PlanNode> nodes = new ArrayList<>();
        for (GeneratedSourceEvidence evidence : execEvidence) {
            nodes.add(execNode(root, scope, evidence, toolLocked, cyclicIds, classesRoot));
        }
        return List.copyOf(nodes);
    }

    private static PlanNode execNode(
            Path root,
            String scope,
            GeneratedSourceEvidence evidence,
            boolean toolLocked,
            Set<String> cyclicIds,
            Path classesRoot) {
        GeneratedSourceStep step = evidence.step();
        ExecGenerationSettings exec = step.exec();
        String subject = "[generated." + scope + "." + step.id() + "]";
        List<PlanBlocker> blockers = new ArrayList<>();
        if (exec.tool().mainClass().isBlank() || exec.tool().coordinates().isEmpty() || !"jvm".equals(exec.tool().runner())) {
            blockers.add(new PlanBlocker(
                    "unresolved-exec-tool",
                    "Exec step " + subject + " references tool `" + exec.toolName() + "` that is not a resolvable jvm tool.",
                    "Declare [generated.execTools." + exec.toolName() + "] with runner = \"jvm\", coordinates, and mainClass."));
        }
        if (!toolLocked) {
            blockers.add(new PlanBlocker(
                    "exec-tool-not-locked",
                    "Exec tooling for " + subject + " is not present in zolt.lock (scope tool-exec).",
                    "Run `zolt resolve` to lock the exec tool coordinates, then rerun `zolt plan`."));
        }
        addInvalidPathBlocker(blockers, root, step.output(), "output");
        for (int index = 0; index < step.inputs().size(); index++) {
            String input = step.inputs().get(index);
            addInvalidPathBlocker(blockers, root, input, "input");
            if (root.resolve(literalBase(input)).normalize().startsWith(classesRoot)) {
                blockers.add(new PlanBlocker(
                        "exec-input-under-compiled-classes",
                        "Exec step " + subject + " reads input `" + input + "` under compiled classes.",
                        "Inputs under compiled classes need tool = \"project\" and post-compile scheduling in a later "
                                + "stage; remove the input or point it at a source path."));
            }
            if (!isGlob(input) && !Files.exists(evidence.inputs().get(index))) {
                blockers.add(new PlanBlocker(
                        "missing-exec-input",
                        "Exec input `" + input + "` for " + subject + " is missing.",
                        "Create the input file or update " + subject + ".inputs."));
            }
        }
        if (cyclicIds.contains(step.id())) {
            blockers.add(new PlanBlocker(
                    "exec-ordering-cycle",
                    "Exec step " + subject + " is part of a cyclic input/output dependency.",
                    "Break the cycle by removing an input that points at another exec step's output."));
        }
        PlanNodeStatus status = blockers.isEmpty()
                ? (evidence.outputExists() && "fresh".equals(evidence.freshness()) ? PlanNodeStatus.SKIPPED : PlanNodeStatus.READY)
                : PlanNodeStatus.BLOCKED;
        List<String> details = new ArrayList<>(List.of(
                "scope: " + scope,
                "tool: " + exec.toolName(),
                "runner: " + exec.tool().runner(),
                "mainClass: " + exec.tool().mainClass(),
                "produces: " + (exec.produces() == null ? "" : exec.produces().configValue()),
                "derivedPosition: " + derivedPosition(scope, exec.produces()),
                "cache: " + exec.cache(),
                "toolLocked: " + toolLocked,
                "outputExists: " + evidence.outputExists(),
                "freshness: " + evidence.freshness()));
        exec.into().ifPresent(into -> details.add("into: " + into));
        details.add("toolCoordinates: " + coordinates(exec));
        return new PlanNode(
                "exec-" + scope + "-" + step.id(),
                "exec-step",
                status,
                "Run pinned exec tool `" + exec.toolName() + "` on declared inputs.",
                step.inputs(),
                List.of(step.output()),
                details,
                blockers);
    }

    private static String derivedPosition(String scope, ProducesLane produces) {
        return produces == ProducesLane.RESOURCES
                ? "before " + scope + " resource copy"
                : "before " + scope + " compile";
    }

    private static String coordinates(ExecGenerationSettings exec) {
        return exec.tool().coordinates().stream()
                .map(coordinate -> coordinate.coordinate() + ":" + coordinate.version().orElse(""))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static Set<String> cyclicStepIds(Path root, List<GeneratedSourceStep> steps) {
        Map<String, Path> outputs = new HashMap<>();
        for (GeneratedSourceStep step : steps) {
            outputs.put(step.id(), root.resolve(step.output()).normalize());
        }
        Map<String, List<String>> successors = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (GeneratedSourceStep step : steps) {
            successors.put(step.id(), new ArrayList<>());
            indegree.put(step.id(), 0);
        }
        for (GeneratedSourceStep consumer : steps) {
            for (String input : consumer.inputs()) {
                Path base = root.resolve(literalBase(input)).normalize();
                for (GeneratedSourceStep producer : steps) {
                    if (!producer.id().equals(consumer.id())
                            && base.startsWith(outputs.get(producer.id()))
                            && !successors.get(producer.id()).contains(consumer.id())) {
                        successors.get(producer.id()).add(consumer.id());
                        indegree.merge(consumer.id(), 1, Integer::sum);
                    }
                }
            }
        }
        Deque<String> ready = new ArrayDeque<>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });
        int removed = 0;
        while (!ready.isEmpty()) {
            String id = ready.poll();
            removed++;
            for (String next : successors.get(id)) {
                if (indegree.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }
        if (removed == steps.size()) {
            return Set.of();
        }
        Set<String> cyclic = new HashSet<>();
        indegree.forEach((id, degree) -> {
            if (degree > 0) {
                cyclic.add(id);
            }
        });
        return cyclic;
    }

    private static void addInvalidPathBlocker(List<PlanBlocker> blockers, Path root, String configuredPath, String field) {
        Path path = Path.of(configuredPath);
        Path resolved = root.resolve(path).normalize();
        if (path.isAbsolute() || !resolved.startsWith(root) || resolved.equals(root)) {
            blockers.add(new PlanBlocker(
                    "invalid-exec-" + field,
                    "Exec " + field + " path `" + configuredPath + "` must be project-relative and under the project directory.",
                    "Use a project-relative path under the project directory."));
        }
    }

    private static boolean isGlob(String input) {
        return input.indexOf('*') >= 0 || input.indexOf('?') >= 0 || input.indexOf('[') >= 0;
    }

    private static String literalBase(String input) {
        int glob = -1;
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            if (character == '*' || character == '?' || character == '[') {
                glob = index;
                break;
            }
        }
        if (glob < 0) {
            return input;
        }
        String prefix = input.substring(0, glob);
        int slash = prefix.lastIndexOf('/');
        return slash < 0 ? "" : prefix.substring(0, slash);
    }
}
