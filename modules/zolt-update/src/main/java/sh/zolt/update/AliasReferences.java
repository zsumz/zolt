package sh.zolt.update;

import sh.zolt.project.DependencyConstraint;
import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.ExecToolCoordinate;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProjectConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds every place a {@code [versions]} alias is referenced across the configuration — dependency
 * scopes, platforms, dependency constraints, OpenAPI/protobuf/exec generated-tool version refs — so
 * both {@code zolt outdated} (governs list) and {@code zolt update} (alias fan-out warning) share
 * one complete reference scan. Coordinate-bearing references also carry their {@code group:artifact}
 * for version discovery. Results are deduplicated by label in a deterministic order.
 */
public final class AliasReferences {
    private static final String PROJECT_TOOL = "project";

    private AliasReferences() {
    }

    public static List<AliasReference> referencing(ProjectConfig config, String alias) {
        Map<String, AliasReference> references = new LinkedHashMap<>();
        collectDependencyReferences(config, alias, references);
        collectConstraintReferences(config, alias, references);
        collectGeneratedReferences(config, alias, references);
        return List.copyOf(references.values());
    }

    public static List<String> referencingLabels(ProjectConfig config, String alias) {
        return referencing(config, alias).stream().map(AliasReference::label).toList();
    }

    private static void collectDependencyReferences(
            ProjectConfig config, String alias, Map<String, AliasReference> references) {
        config.dependencyMetadata().values().stream()
                .filter(metadata -> alias.equals(metadata.versionRef()))
                .sorted(Comparator.comparing(DependencyMetadata::section).thenComparing(DependencyMetadata::coordinate))
                .forEach(metadata -> add(
                        references,
                        "[" + metadata.section() + "]." + metadata.coordinate(),
                        Optional.of(metadata.coordinate())));
    }

    private static void collectConstraintReferences(
            ProjectConfig config, String alias, Map<String, AliasReference> references) {
        config.dependencyPolicy().constraints().values().stream()
                .filter(constraint -> constraint.versionRef().filter(alias::equals).isPresent())
                .sorted(Comparator.comparing(DependencyConstraint::coordinate))
                .forEach(constraint -> add(
                        references,
                        "[dependencyConstraints]." + constraint.coordinate(),
                        Optional.of(constraint.coordinate())));
    }

    private static void collectGeneratedReferences(
            ProjectConfig config, String alias, Map<String, AliasReference> references) {
        List<GeneratedSourceStep> steps = new ArrayList<>(config.build().generatedMainSources());
        steps.addAll(config.build().generatedTestSources());
        for (GeneratedSourceStep step : steps) {
            collectOpenApiReference(step, alias, references);
            collectExecReferences(step, alias, references);
            collectProtobufReferences(step, alias, references);
        }
    }

    private static void collectOpenApiReference(
            GeneratedSourceStep step, String alias, Map<String, AliasReference> references) {
        if (step.openApi().toolVersionRef().filter(alias::equals).isPresent()) {
            add(references, "[generated.openapiTool].versionRef", step.openApi().toolCoordinate());
        }
    }

    private static void collectExecReferences(
            GeneratedSourceStep step, String alias, Map<String, AliasReference> references) {
        if (step.kind() != GeneratedSourceKind.EXEC || PROJECT_TOOL.equals(step.exec().toolName())) {
            return;
        }
        for (ExecToolCoordinate coordinate : step.exec().tool().coordinates()) {
            if (coordinate.versionRef().filter(alias::equals).isPresent()) {
                add(
                        references,
                        "[generated.execTools." + step.exec().toolName() + "].coordinates",
                        Optional.of(coordinate.coordinate()));
            }
        }
    }

    private static void collectProtobufReferences(
            GeneratedSourceStep step, String alias, Map<String, AliasReference> references) {
        if (step.kind() != GeneratedSourceKind.PROTOBUF) {
            return;
        }
        if (step.protobuf().protocVersionRef().filter(alias::equals).isPresent()) {
            add(references, "[generated.protobufTool].protocVersionRef", step.protobuf().protocCoordinate());
        }
        if (step.protobuf().grpcPluginVersionRef().filter(alias::equals).isPresent()) {
            add(references, "[generated.protobufTool].grpcPluginVersionRef", step.protobuf().grpcPluginCoordinate());
        }
    }

    private static void add(Map<String, AliasReference> references, String label, Optional<String> coordinate) {
        references.putIfAbsent(label, new AliasReference(label, coordinate));
    }
}
