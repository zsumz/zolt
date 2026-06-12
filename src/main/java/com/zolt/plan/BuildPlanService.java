package com.zolt.plan;

import com.zolt.generated.GeneratedSourceEvidence;
import com.zolt.generated.GeneratedSourceEvidenceService;
import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.TestRuntimeSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BuildPlanService {
    private final GeneratedSourceEvidenceService generatedSourceEvidenceService;

    public BuildPlanService() {
        this(new GeneratedSourceEvidenceService());
    }

    BuildPlanService(GeneratedSourceEvidenceService generatedSourceEvidenceService) {
        this.generatedSourceEvidenceService = generatedSourceEvidenceService;
    }

    public BuildPlan plan(Path projectRoot, ProjectConfig config, PlanTarget target, Optional<Path> reportsDir) {
        Path root = projectRoot.toAbsolutePath().normalize();
        List<PlanNode> nodes = new ArrayList<>();
        List<GeneratedSourceEvidence> generatedSources =
                generatedSourceEvidenceService.evidence(root, config.build());
        addLockfileNode(nodes, root);
        addBuildNodes(nodes, root, config, generatedSources);
        if (target.includesTests()) {
            addTestNodes(nodes, root, config, reportsDir, generatedSources);
        }
        if (target.includesCoverage()) {
            addCoverageNode(nodes);
        }
        if (target.includesPackage()) {
            addPackageNode(nodes, config);
        }
        if (target.includesPublish()) {
            addPublishNode(nodes);
        }
        return new BuildPlan(1, root, config.project().name(), target, nodes);
    }

    private static void addLockfileNode(List<PlanNode> nodes, Path root) {
        Path lockfile = root.resolve("zolt.lock");
        if (Files.isRegularFile(lockfile)) {
            nodes.add(new PlanNode(
                    "lockfile",
                    "resolve",
                    PlanNodeStatus.READY,
                    "Read existing zolt.lock without refreshing dependency metadata.",
                    List.of("zolt.toml", "zolt.lock"),
                    List.of(),
                    List.of("freshness: verify with `zolt resolve --locked` or `zolt check --check lockfile`"),
                    List.of()));
            return;
        }
        nodes.add(new PlanNode(
                "lockfile",
                "resolve",
                PlanNodeStatus.BLOCKED,
                "Dependency graph is not locked yet.",
                List.of("zolt.toml"),
                List.of("zolt.lock"),
                List.of(),
                List.of(new PlanBlocker(
                        "missing-lockfile",
                        "zolt.lock is missing; plan will not resolve or download artifacts.",
                        "Run `zolt resolve` first, then rerun `zolt plan`."))));
    }

    private static void addBuildNodes(
            List<PlanNode> nodes,
            Path root,
            ProjectConfig config,
            List<GeneratedSourceEvidence> generatedSources) {
        BuildSettings build = config.build();
        for (GeneratedSourceEvidence evidence : generatedSourcesForScope(generatedSources, "main")) {
            nodes.add(generatedSourceNode(root, "generate-main-" + evidence.step().id(), evidence));
        }
        addResourceNode(nodes, "process-main-resources", "resources", build.resourceRoots(), build.output(), build.resourceFiltering(), false);
        nodes.add(new PlanNode(
                "compile-main",
                "compile",
                PlanNodeStatus.READY,
                "Compile main Java sources with Zolt-owned javac inputs.",
                mainCompileInputs(build),
                List.of(build.output()),
                List.of("source: " + build.source()),
                List.of()));
    }

    private static void addTestNodes(
            List<PlanNode> nodes,
            Path root,
            ProjectConfig config,
            Optional<Path> reportsDir,
            List<GeneratedSourceEvidence> generatedSources) {
        BuildSettings build = config.build();
        for (GeneratedSourceEvidence evidence : generatedSourcesForScope(generatedSources, "test")) {
            nodes.add(generatedSourceNode(root, "generate-test-" + evidence.step().id(), evidence));
        }
        addResourceNode(nodes, "process-test-resources", "test-resources", build.testResourceRoots(), build.testOutput(), build.resourceFiltering(), true);
        List<String> testCompileInputs = new ArrayList<>();
        testCompileInputs.add(build.output());
        testCompileInputs.addAll(build.testSources());
        testCompileInputs.addAll(build.groovyTestSources());
        for (GeneratedSourceStep step : build.generatedTestSources()) {
            testCompileInputs.add(step.output());
        }
        nodes.add(new PlanNode(
                "compile-tests",
                "compile",
                PlanNodeStatus.READY,
                "Compile Java and configured Groovy test sources.",
                testCompileInputs,
                List.of(build.testOutput()),
                testCompileDetails(build),
                List.of()));
        TestRuntimeSettings runtime = build.testRuntime();
        List<String> outputs = reportsDir.map(path -> List.of(path.toString())).orElseGet(List::of);
        List<String> details = new ArrayList<>();
        if (!runtime.jvmArgs().isEmpty()) {
            details.add("jvmArgs: " + runtime.jvmArgs().size());
        }
        if (!runtime.systemProperties().isEmpty()) {
            details.add("systemProperties: " + runtime.systemProperties().keySet());
        }
        if (!runtime.environment().isEmpty()) {
            details.add("environment: " + runtime.environment().keySet() + " (values redacted)");
        }
        if (!runtime.events().isEmpty()) {
            details.add("events: " + String.join(",", runtime.events()));
        }
        nodes.add(new PlanNode(
                "run-tests",
                "test",
                PlanNodeStatus.READY,
                "Run tests through Zolt's JUnit Platform path.",
                List.of(build.testOutput(), build.output(), "zolt.lock"),
                outputs,
                details,
                List.of()));
    }

    private static void addResourceNode(
            List<PlanNode> nodes,
            String id,
            String kind,
            List<String> roots,
            String output,
            ResourceFilteringSettings filtering,
            boolean testResources) {
        List<String> details = new ArrayList<>();
        boolean filteringEnabled = testResources ? filtering.testEnabled() : filtering.enabled();
        details.add("filtering: " + (filteringEnabled ? "enabled" : "disabled"));
        if (filteringEnabled) {
            details.add("filterIncludes: " + filtering.includes());
            details.add("missingTokens: " + filtering.missing().configValue());
            details.add("tokens: " + filtering.tokens().keySet());
        }
        nodes.add(new PlanNode(
                id,
                kind,
                roots.isEmpty() ? PlanNodeStatus.SKIPPED : PlanNodeStatus.READY,
                testResources ? "Copy configured test resources." : "Copy configured main resources.",
                roots,
                List.of(output),
                details,
                List.of()));
    }

    private static void addCoverageNode(List<PlanNode> nodes) {
        nodes.add(new PlanNode(
                "coverage",
                "coverage",
                PlanNodeStatus.PLANNED,
                "Coverage runs only through the explicit zolt coverage command, not hidden work after tests.",
                List.of("target/test-classes", "zolt.lock"),
                List.of("target/coverage"),
                List.of("command: zolt coverage"),
                List.of()));
    }

    private static void addPackageNode(List<PlanNode> nodes, ProjectConfig config) {
        PackageMode mode = config.packageSettings().mode();
        List<PlanBlocker> blockers = new ArrayList<>();
        if (mode == PackageMode.UBER) {
            blockers.add(new PlanBlocker(
                    "unsupported-package-mode",
                    "Package mode `uber` is recognized but not implemented.",
                    "Use thin, spring-boot, war, spring-boot-war, or quarkus until uber packaging is implemented."));
        }
        if ((mode == PackageMode.SPRING_BOOT || mode == PackageMode.SPRING_BOOT_WAR)
                && config.project().main().isEmpty()) {
            blockers.add(new PlanBlocker(
                    "missing-main-class",
                    "Spring Boot package modes require [project].main.",
                    "Add [project].main to zolt.toml."));
        }
        nodes.add(new PlanNode(
                "assemble-package",
                "package",
                blockers.isEmpty() ? PlanNodeStatus.READY : PlanNodeStatus.BLOCKED,
                "Assemble the configured Zolt package artifact.",
                List.of(config.build().output(), "zolt.lock"),
                packageOutputs(config),
                List.of("mode: " + mode.configValue()),
                blockers));
    }

    private static void addPublishNode(List<PlanNode> nodes) {
        nodes.add(new PlanNode(
                "publish-dry-run",
                "publish",
                PlanNodeStatus.PLANNED,
                "Publication dry run is planned as explicit Zolt behavior.",
                List.of("target/package-artifact", "zolt.lock"),
                List.of("publish request preview"),
                List.of("mode: dry-run"),
                List.of()));
    }

    private static PlanNode generatedSourceNode(Path root, String id, GeneratedSourceEvidence evidence) {
        GeneratedSourceStep step = evidence.step();
        List<PlanBlocker> blockers = new ArrayList<>();
        if (step.kind() != GeneratedSourceKind.DECLARED_ROOT && step.kind() != GeneratedSourceKind.OPENAPI) {
            blockers.add(new PlanBlocker(
                    "unsupported-generated-source-kind",
                    "Generated source kind `" + step.kind().configValue() + "` is not supported yet.",
                    "Use declared-root or add support for a Zolt-owned typed generator."));
        }
        if (step.kind() == GeneratedSourceKind.OPENAPI
                && (step.openApi().toolCoordinate().isEmpty()
                        || step.openApi().toolVersion().isEmpty()
                        || step.openApi().generator().isEmpty())) {
            blockers.add(new PlanBlocker(
                    "openapi-generation-incomplete",
                    "OpenAPI generated-source step `" + step.id() + "` is missing tool or generator settings.",
                    "Add [generated.openapiTool] coordinate/version and generator or preset.generator."));
        }
        if (!"java".equals(step.language())) {
            blockers.add(new PlanBlocker(
                    "unsupported-generated-source-language",
                    "Generated source language `" + step.language() + "` is not supported yet.",
                    "Use language = \"java\" for current generated-source steps."));
        }
        addInvalidPathBlocker(blockers, root, step.output(), "output");
        for (int index = 0; index < step.inputs().size(); index++) {
            String input = step.inputs().get(index);
            addInvalidPathBlocker(blockers, root, input, "input");
            if (!Files.exists(evidence.inputs().get(index))) {
                blockers.add(new PlanBlocker(
                        "missing-generated-source-input",
                        "Generated source input `" + input + "` is missing.",
                        "Create the input file or update [generated." + evidence.scope() + "." + step.id() + "].inputs."));
            }
        }
        if (step.required() && step.kind() == GeneratedSourceKind.DECLARED_ROOT && !evidence.outputExists()) {
            blockers.add(new PlanBlocker(
                    "missing-generated-source-output",
                    "Required generated source output `" + step.output() + "` is missing.",
                    "Run the generator that produces it, commit the generated sources, or remove the generated-source step."));
        }
        if (step.required() && "stale".equals(evidence.freshness())) {
            blockers.add(new PlanBlocker(
                    "stale-generated-source-output",
                    "Required generated source output `" + step.output() + "` is older than one or more declared inputs.",
                    "Regenerate the source root or update [generated." + evidence.scope() + "." + step.id() + "].inputs."));
        }
        PlanNodeStatus status = blockers.isEmpty()
                ? (step.required() || evidence.outputExists() ? PlanNodeStatus.READY : PlanNodeStatus.SKIPPED)
                : PlanNodeStatus.BLOCKED;
        List<String> details = new ArrayList<>(List.of(
                "sourceRoot: " + evidence.sourceRootId(),
                "scope: " + evidence.scope(),
                "compileLane: " + evidence.compileLane(),
                "kind: " + step.kind().configValue(),
                "language: " + step.language(),
                "ownership: " + evidence.ownership(),
                "outputExists: " + evidence.outputExists(),
                "inputsPresent: " + evidence.inputsPresent(),
                "freshness: " + evidence.freshness(),
                "toolArtifact: " + evidence.toolArtifact()));
        step.openApi().toolVersionRef().ifPresent(versionRef -> details.add("toolVersionRef: " + versionRef));
        details.add("toolFingerprint: " + evidence.toolFingerprint());
        details.add("optionsFingerprint: " + evidence.optionsFingerprint());
        return new PlanNode(
                id,
                "generated-source",
                status,
                step.kind() == GeneratedSourceKind.DECLARED_ROOT
                        ? "Use declared generated Java source root."
                        : "Run typed generated-source step.",
                step.inputs(),
                List.of(step.output()),
                details,
                blockers);
    }

    private static List<GeneratedSourceEvidence> generatedSourcesForScope(
            List<GeneratedSourceEvidence> generatedSources,
            String scope) {
        return generatedSources.stream()
                .filter(evidence -> evidence.scope().equals(scope))
                .toList();
    }

    private static void addInvalidPathBlocker(
            List<PlanBlocker> blockers,
            Path root,
            String configuredPath,
            String field) {
        Path path = Path.of(configuredPath);
        Path resolved = root.resolve(path).normalize();
        if (path.isAbsolute() || !resolved.startsWith(root) || resolved.equals(root)) {
            blockers.add(new PlanBlocker(
                    "invalid-generated-source-" + field,
                    "Generated source " + field + " path `" + configuredPath + "` must be project-relative and under the project directory.",
                    "Use a project-relative path under the project directory."));
        }
    }

    private static List<String> mainCompileInputs(BuildSettings build) {
        List<String> inputs = new ArrayList<>();
        inputs.add(build.source());
        for (GeneratedSourceStep step : build.generatedMainSources()) {
            inputs.add(step.output());
        }
        return List.copyOf(inputs);
    }

    private static List<String> testCompileDetails(BuildSettings build) {
        List<String> details = new ArrayList<>();
        if (!build.testSources().isEmpty()) {
            details.add("javaTestRoots: " + build.testSources());
        }
        if (!build.groovyTestSources().isEmpty()) {
            details.add("groovyTestRoots: " + build.groovyTestSources());
        }
        return List.copyOf(details);
    }

    private static List<String> packageOutputs(ProjectConfig config) {
        String extension = switch (config.packageSettings().mode()) {
            case WAR, SPRING_BOOT_WAR -> ".war";
            default -> ".jar";
        };
        String base = "target/" + config.project().name() + "-" + config.project().version();
        List<String> outputs = new ArrayList<>();
        outputs.add(base + extension);
        if (config.packageSettings().sources()) {
            outputs.add(base + "-sources.jar");
        }
        if (config.packageSettings().javadoc()) {
            outputs.add(base + "-javadoc.jar");
        }
        if (config.packageSettings().tests()) {
            outputs.add(base + "-tests.jar");
        }
        return List.copyOf(outputs);
    }
}
