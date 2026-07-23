package sh.zolt.update;

import sh.zolt.dependency.VersionCandidates;
import sh.zolt.dependency.VersionClassifier;
import sh.zolt.maven.metadata.MetadataDiscovery;
import sh.zolt.maven.metadata.VersionDiscovery;
import sh.zolt.maven.repository.RepositoryAccess;
import sh.zolt.maven.repository.RepositoryAccessPlanner;
import sh.zolt.project.DependencyConstraint;
import sh.zolt.project.DependencyPolicySettings;
import sh.zolt.project.DependencySection;
import sh.zolt.project.ProjectConfig;
import sh.zolt.toml.dependency.ProjectConfigDependencyMutator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plans and applies dependency-version updates. Planning discovers candidates per surface, picks the
 * target at the requested ceiling, and records applicable changes as edits and unsupported ones as
 * skips. Applying uses ONLY existing mutation machinery — {@code withVersionAliases} for aliases,
 * {@code ProjectConfigDependencyMutator} for literal dependencies and platforms, and a
 * kind/reason-preserving rebuild for constraints — never writing a literal over a versionRef.
 */
public final class UpdateEngine {
    private static final Comparator<UpdateEdit> EDIT_ORDER = Comparator
            .comparingInt((UpdateEdit edit) -> edit.surface() == OutdatedSurface.VERSION_ALIAS ? 0 : 1)
            .thenComparing(UpdateEdit::identifier)
            .thenComparing(UpdateEdit::section);

    private final SurfaceDiscovery surfaceDiscovery;
    private final RepositoryAccessPlanner planner;
    private final SurfaceCollector collector = new SurfaceCollector();
    private final VersionClassifier classifier = new VersionClassifier();

    public UpdateEngine(VersionDiscovery discovery) {
        this(discovery, new RepositoryAccessPlanner());
    }

    public UpdateEngine(VersionDiscovery discovery, RepositoryAccessPlanner planner) {
        this.surfaceDiscovery = new SurfaceDiscovery(discovery);
        this.planner = planner;
    }

    public UpdatePlan plan(ProjectConfig config, UpdateOptions options) {
        List<RepositoryAccess> repositories = planner.plan(config);
        Map<String, MetadataDiscovery> memo = new LinkedHashMap<>();
        List<UpdateEdit> edits = new ArrayList<>();
        List<UpdateSkip> skips = new ArrayList<>();
        for (SurfaceRequest surface : collector.collect(config)) {
            if (!Selectors.matches(surface.identifier(), surface.section(), surface.surface().jsonName(), options.selectors())) {
                continue;
            }
            MetadataDiscovery discovered = surfaceDiscovery.discover(surface, repositories, options.offline(), memo);
            if (!discovered.resolved()) {
                continue;
            }
            VersionCandidates candidates =
                    classifier.candidates(surface.currentVersion(), discovered.versions(), options.includePrereleases());
            Optional<String> target = options.ceiling().target(candidates);
            if (target.isEmpty() || target.orElseThrow().equals(surface.currentVersion())) {
                continue;
            }
            recordChange(surface, target.orElseThrow(), edits, skips);
        }
        edits.sort(EDIT_ORDER);
        return new UpdatePlan(edits, skips, aliasFanOutWarnings(edits));
    }

    private void recordChange(SurfaceRequest surface, String target, List<UpdateEdit> edits, List<UpdateSkip> skips) {
        if (!UpdateApplicability.isApplicable(surface.surface())) {
            skips.add(new UpdateSkip(
                    surface.surface(),
                    surface.identifier(),
                    surface.section(),
                    UpdateApplicability.reason(surface.surface())));
            return;
        }
        edits.add(new UpdateEdit(
                surface.surface(),
                surface.identifier(),
                surface.section(),
                surface.currentVersion(),
                target,
                classifier.classify(surface.currentVersion(), target),
                surface.governs()));
    }

    public ProjectConfig apply(ProjectConfig config, UpdatePlan plan) {
        ProjectConfig updated = config;
        Map<String, String> aliases = new LinkedHashMap<>(config.versionAliases());
        boolean aliasChanged = false;
        for (UpdateEdit edit : plan.edits()) {
            switch (edit.surface()) {
                case VERSION_ALIAS -> {
                    aliases.put(edit.identifier(), edit.toVersion());
                    aliasChanged = true;
                }
                case DEPENDENCY, ANNOTATION_PROCESSOR -> updated = ProjectConfigDependencyMutator.addDependency(
                        updated, sectionOf(edit.section()), edit.identifier(), edit.toVersion());
                case PLATFORM ->
                    updated = ProjectConfigDependencyMutator.addPlatform(updated, edit.identifier(), edit.toVersion());
                case DEPENDENCY_CONSTRAINT -> updated = applyConstraint(updated, edit.identifier(), edit.toVersion());
                default -> {
                    // Non-applicable surfaces are reported as skips and never mutated.
                }
            }
        }
        return aliasChanged ? updated.withVersionAliases(aliases) : updated;
    }

    private static ProjectConfig applyConstraint(ProjectConfig config, String coordinate, String version) {
        Map<String, DependencyConstraint> constraints = new LinkedHashMap<>(config.dependencyPolicy().constraints());
        DependencyConstraint existing = constraints.get(coordinate);
        if (existing == null) {
            return config;
        }
        constraints.put(
                coordinate,
                new DependencyConstraint(coordinate, version, existing.versionRef(), existing.kind(), existing.reason()));
        DependencyPolicySettings policy = new DependencyPolicySettings(
                config.dependencyPolicy().exclusions(), constraints, config.dependencyPolicy().failOnVersionConflict());
        return config.withDependencyPolicy(policy);
    }

    private static DependencySection sectionOf(String section) {
        return switch (section) {
            case "[dependencies]" -> DependencySection.MAIN;
            case "[api.dependencies]" -> DependencySection.API;
            case "[runtime.dependencies]" -> DependencySection.RUNTIME;
            case "[provided.dependencies]" -> DependencySection.PROVIDED;
            case "[dev.dependencies]" -> DependencySection.DEV;
            case "[test.dependencies]" -> DependencySection.TEST;
            case "[annotationProcessors]" -> DependencySection.PROCESSOR;
            case "[test.annotationProcessors]" -> DependencySection.TEST_PROCESSOR;
            default -> throw new IllegalStateException("Unmapped dependency section: " + section);
        };
    }

    private static List<String> aliasFanOutWarnings(List<UpdateEdit> edits) {
        List<String> warnings = new ArrayList<>();
        for (UpdateEdit edit : edits) {
            if (edit.surface() == OutdatedSurface.VERSION_ALIAS) {
                warnings.add("Alias `" + edit.identifier() + "` " + edit.fromVersion() + " -> " + edit.toVersion()
                        + " updates " + edit.fanOut().size() + " referencing coordinate(s): "
                        + String.join(", ", edit.fanOut()) + ".");
            }
        }
        return warnings;
    }
}
