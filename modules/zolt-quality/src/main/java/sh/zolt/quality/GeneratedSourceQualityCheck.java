package sh.zolt.quality;

import sh.zolt.generated.GeneratedSourceEvidence;
import sh.zolt.generated.GeneratedSourceEvidenceService;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProducesLane;
import sh.zolt.project.ProjectConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GeneratedSourceQualityCheck {
    private final GeneratedSourceEvidenceService generatedSourceEvidenceService;

    GeneratedSourceQualityCheck(GeneratedSourceEvidenceService generatedSourceEvidenceService) {
        this.generatedSourceEvidenceService = generatedSourceEvidenceService;
    }

    List<QualityCheckResult> check(
            Optional<String> member,
            Path projectRoot,
            ProjectConfig config) {
        return check(member, projectRoot, config, false);
    }

    List<QualityCheckResult> check(
            Optional<String> member,
            Path projectRoot,
            ProjectConfig config,
            boolean requireOfflineReady) {
        List<GeneratedSourceCheckStep> steps = generatedSourceSteps(config.build());
        if (steps.isEmpty()) {
            return List.of(QualityCheckResult.passed(
                    QualityCheckService.GENERATED_SOURCES,
                    member,
                    config.project().name(),
                    "No declared generated-source steps require validation."));
        }

        List<QualityCheckResult> results = new ArrayList<>();
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        String outputRoot = config.build().outputRoot();
        Map<String, GeneratedSourceEvidence> evidenceByKey = generatedSourceEvidenceByKey(normalizedRoot, config);
        for (GeneratedSourceCheckStep checkStep : steps) {
            GeneratedSourceStep step = checkStep.step();
            Optional<QualityCheckResult> invalid =
                    invalidGeneratedSourceStep(member, normalizedRoot, outputRoot, checkStep);
            if (invalid.isPresent()) {
                results.add(invalid.orElseThrow());
                continue;
            }

            String subject = generatedSection(checkStep);
            if (requireOfflineReady && step.kind() == GeneratedSourceKind.EXEC && "none".equals(step.exec().cache())) {
                results.add(QualityCheckResult.failed(
                        QualityCheckService.GENERATED_SOURCES,
                        member,
                        subject,
                        "Exec step uses cache = \"none\", which always runs and is not offline-reproducible.",
                        "Commit a deterministic input and use cache = \"content\", or drop --require-offline-ready."));
                continue;
            }
            GeneratedSourceEvidence evidence = evidenceByKey.get(generatedSourceKey(checkStep.scope(), step.id()));
            Optional<String> missingInput = firstMissingGeneratedInput(step, evidence);
            if (missingInput.isPresent()) {
                results.add(QualityCheckResult.failed(
                        QualityCheckService.GENERATED_SOURCES,
                        member,
                        subject,
                        "Generated source input `" + missingInput.orElseThrow() + "` is missing.",
                        "Create the input file or update " + subject + ".inputs."));
                continue;
            }
            if (!evidence.outputExists()) {
                if (step.required()) {
                    results.add(QualityCheckResult.failed(
                            QualityCheckService.GENERATED_SOURCES,
                            member,
                            subject,
                            "Generated source root `" + step.output() + "` is missing.",
                            "Run the generator that produces it, commit the generated sources, or remove "
                                    + subject
                                    + " until Zolt supports that generator."));
                    continue;
                }
                results.add(QualityCheckResult.skipped(
                        QualityCheckService.GENERATED_SOURCES,
                        member,
                        subject,
                        "Optional generated source root `" + step.output() + "` is missing.",
                        "Generate it when needed, or set required = true if the root must exist for builds."));
                continue;
            }
            if ("stale".equals(evidence.freshness())) {
                results.add(QualityCheckResult.failed(
                        QualityCheckService.GENERATED_SOURCES,
                        member,
                        subject,
                        "Generated source root `" + step.output() + "` is stale; one or more declared inputs are newer.",
                        "Regenerate the source root or update " + subject + ".inputs."));
                continue;
            }

            results.add(QualityCheckResult.passed(
                    QualityCheckService.GENERATED_SOURCES,
                    member,
                    subject,
                    "Generated source root `"
                            + step.output()
                            + "` is declared and exported as IDE source root `generated-"
                            + checkStep.scope()
                            + "-"
                            + step.id()
                            + "` with ownership `"
                            + evidence.ownership()
                            + "` and freshness `"
                            + evidence.freshness()
                            + "`."));
        }
        return List.copyOf(results);
    }

    private Map<String, GeneratedSourceEvidence> generatedSourceEvidenceByKey(Path projectRoot, ProjectConfig config) {
        Map<String, GeneratedSourceEvidence> evidence = new LinkedHashMap<>();
        for (GeneratedSourceEvidence generatedSource : generatedSourceEvidenceService.evidence(projectRoot, config.build())) {
            evidence.put(generatedSourceKey(generatedSource.scope(), generatedSource.step().id()), generatedSource);
        }
        return Map.copyOf(evidence);
    }

    private static String generatedSourceKey(String scope, String id) {
        return scope + ":" + id;
    }

    private static Optional<String> firstMissingGeneratedInput(
            GeneratedSourceStep step,
            GeneratedSourceEvidence evidence) {
        for (int index = 0; index < step.inputs().size(); index++) {
            if (!Files.exists(evidence.inputs().get(index))) {
                return Optional.of(step.inputs().get(index));
            }
        }
        return Optional.empty();
    }

    private static List<GeneratedSourceCheckStep> generatedSourceSteps(BuildSettings build) {
        List<GeneratedSourceCheckStep> steps = new ArrayList<>();
        for (GeneratedSourceStep step : build.generatedMainSources()) {
            steps.add(new GeneratedSourceCheckStep("main", step));
        }
        for (GeneratedSourceStep step : build.generatedTestSources()) {
            steps.add(new GeneratedSourceCheckStep("test", step));
        }
        return List.copyOf(steps);
    }

    private static Optional<QualityCheckResult> invalidGeneratedSourceStep(
            Optional<String> member,
            Path projectRoot,
            String outputRoot,
            GeneratedSourceCheckStep checkStep) {
        GeneratedSourceStep step = checkStep.step();
        String subject = generatedSection(checkStep);
        if (step.kind() != GeneratedSourceKind.DECLARED_ROOT
                && step.kind() != GeneratedSourceKind.OPENAPI
                && step.kind() != GeneratedSourceKind.PROTOBUF
                && step.kind() != GeneratedSourceKind.EXEC) {
            return Optional.of(QualityCheckResult.failed(
                    QualityCheckService.GENERATED_SOURCES,
                    member,
                    subject,
                    "Unsupported generated source kind `" + step.kind().configValue() + "`.",
                    "Use declared-root for already generated Java sources."));
        }
        if (step.kind() == GeneratedSourceKind.EXEC) {
            Optional<QualityCheckResult> invalidExec = invalidExecStep(member, projectRoot, outputRoot, checkStep);
            if (invalidExec.isPresent()) {
                return invalidExec;
            }
        }
        if (!"java".equals(step.language())) {
            return Optional.of(QualityCheckResult.failed(
                    QualityCheckService.GENERATED_SOURCES,
                    member,
                    subject,
                    "Unsupported generated source language `" + step.language() + "`.",
                    "Use language = \"java\" for MVP generated-source steps."));
        }
        Optional<QualityCheckResult> invalidOutput = invalidGeneratedPath(
                member,
                projectRoot,
                checkStep,
                "output",
                step.output());
        if (invalidOutput.isPresent()) {
            return invalidOutput;
        }
        for (String input : step.inputs()) {
            Optional<QualityCheckResult> invalidInput = invalidGeneratedPath(
                    member,
                    projectRoot,
                    checkStep,
                    "inputs",
                    input);
            if (invalidInput.isPresent()) {
                return invalidInput;
            }
        }
        return Optional.empty();
    }

    private static Optional<QualityCheckResult> invalidExecStep(
            Optional<String> member,
            Path projectRoot,
            String outputRoot,
            GeneratedSourceCheckStep checkStep) {
        GeneratedSourceStep step = checkStep.step();
        String subject = generatedSection(checkStep);
        Optional<QualityCheckResult> invalidTool = invalidExecTool(member, subject, step);
        if (invalidTool.isPresent()) {
            return invalidTool;
        }
        Optional<QualityCheckResult> invalidLane = invalidExecLane(member, subject, checkStep);
        if (invalidLane.isPresent()) {
            return invalidLane;
        }
        if (isPostCompile(step, projectRoot, outputRoot)
                && (step.exec().produces() == ProducesLane.JAVA_SOURCES
                        || step.exec().produces() == ProducesLane.TEST_SOURCES)) {
            return Optional.of(QualityCheckResult.failed(
                    QualityCheckService.GENERATED_SOURCES,
                    member,
                    subject,
                    "Exec step runs after compilation but produces " + step.exec().produces().configValue()
                            + ", which would feed that same compile.",
                    "Post-compile steps may only produce resources, test-resources, or intermediate."));
        }
        return Optional.empty();
    }

    private static Optional<QualityCheckResult> invalidExecTool(
            Optional<String> member, String subject, GeneratedSourceStep step) {
        String runner = step.exec().tool().runner();
        String toolName = step.exec().toolName();
        boolean resolvable = switch (runner) {
            case "jvm" -> !step.exec().tool().mainClass().isBlank() && !step.exec().tool().coordinates().isEmpty();
            case "process" -> !step.exec().tool().binary().isBlank() && !step.exec().tool().versionCommand().isEmpty();
            case "project" -> !step.exec().tool().mainClass().isBlank();
            default -> false;
        };
        if (!resolvable) {
            return Optional.of(QualityCheckResult.failed(
                    QualityCheckService.GENERATED_SOURCES,
                    member,
                    subject,
                    "Exec step references tool `" + toolName + "` (runner `" + runner + "`) that is not resolvable.",
                    "Declare the tool with a supported runner (jvm/process) or use tool = \"project\" with a mainClass."));
        }
        if ("process".equals(runner) && !step.exec().tool().allowUnpinnedTool()) {
            return Optional.of(QualityCheckResult.failed(
                    QualityCheckService.GENERATED_SOURCES,
                    member,
                    subject,
                    "Exec step runs unpinned PATH binary `" + step.exec().tool().binary()
                            + "` without acknowledging that its bytes are unprovable.",
                    "Set allowUnpinnedTool = true on [generated.execTools." + toolName + "]."));
        }
        return Optional.empty();
    }

    private static Optional<QualityCheckResult> invalidExecLane(
            Optional<String> member, String subject, GeneratedSourceCheckStep checkStep) {
        GeneratedSourceStep step = checkStep.step();
        ProducesLane produces = step.exec().produces();
        if ("main".equals(checkStep.scope())) {
            if (produces == ProducesLane.TEST_SOURCES) {
                return Optional.of(laneFailure(member, subject, "test-sources",
                        "[generated.test." + step.id() + "]", "java-sources"));
            }
            if (produces == ProducesLane.TEST_RESOURCES) {
                return Optional.of(laneFailure(member, subject, "test-resources",
                        "[generated.test." + step.id() + "]", "resources"));
            }
        }
        if ("test".equals(checkStep.scope())) {
            if (produces == ProducesLane.JAVA_SOURCES) {
                return Optional.of(laneFailure(member, subject, "java-sources",
                        "[generated.main." + step.id() + "]", "test-sources"));
            }
            if (produces == ProducesLane.RESOURCES) {
                return Optional.of(laneFailure(member, subject, "resources",
                        "[generated.main." + step.id() + "]", "test-resources"));
            }
        }
        return Optional.empty();
    }

    private static QualityCheckResult laneFailure(
            Optional<String> member, String subject, String lane, String otherSection, String otherLane) {
        return QualityCheckResult.failed(
                QualityCheckService.GENERATED_SOURCES,
                member,
                subject,
                "Exec step produces " + lane + " but is declared in the wrong lane.",
                "Move it to " + otherSection + " or set produces = \"" + otherLane + "\".");
    }

    private static boolean isPostCompile(GeneratedSourceStep step, Path projectRoot, String outputRoot) {
        if ("project".equals(step.exec().tool().runner())) {
            return true;
        }
        Path classes = projectRoot.resolve(outputRoot).resolve("classes").normalize();
        Path testClasses = projectRoot.resolve(outputRoot).resolve("test-classes").normalize();
        for (String input : step.inputs()) {
            Path base = projectRoot.resolve(literalBase(input)).normalize();
            if (base.startsWith(classes) || base.startsWith(testClasses)) {
                return true;
            }
        }
        return false;
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

    private static Optional<QualityCheckResult> invalidGeneratedPath(
            Optional<String> member,
            Path projectRoot,
            GeneratedSourceCheckStep checkStep,
            String field,
            String configuredPath) {
        Path configured = Path.of(configuredPath);
        Path resolved = projectRoot.resolve(configured).normalize();
        if (configured.isAbsolute() || !resolved.startsWith(projectRoot) || resolved.equals(projectRoot)) {
            return Optional.of(QualityCheckResult.failed(
                    QualityCheckService.GENERATED_SOURCES,
                    member,
                    generatedSection(checkStep) + "." + field,
                    "Invalid generated source " + field + " path `" + configuredPath + "`.",
                    "Use a project-relative path under the project directory."));
        }
        return Optional.empty();
    }

    private static String generatedSection(GeneratedSourceCheckStep checkStep) {
        return "[generated." + checkStep.scope() + "." + checkStep.step().id() + "]";
    }

    private record GeneratedSourceCheckStep(String scope, GeneratedSourceStep step) {
    }
}
