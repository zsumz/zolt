package com.zolt.quality;

import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceDiscoveryService;
import com.zolt.workspace.WorkspaceMemberSelector;
import com.zolt.workspace.WorkspaceSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class QualityCheckService {
    public static final String COMMAND_SURFACE = "command-surface";

    private static final Set<String> IMPLEMENTED_CHECKS = Set.of(COMMAND_SURFACE);
    private static final Map<String, String> PLANNED_CHECK_NOTES = Map.of(
            "lockfile", "followUps/-add-lockfile-and-project-model-checks.md",
            "project-model", "followUps/-add-lockfile-and-project-model-checks.md",
            "dependency-metadata", "followUps/-add-dependency-metadata-checks.md",
            "package-metadata", "followUps/-add-package-and-manifest-checks.md",
            "manifest-metadata", "followUps/-add-package-and-manifest-checks.md",
            "generated-sources", "followUps/-add-generated-source-quality-checks.md");

    private final ZoltTomlParser projectParser;
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final WorkspaceMemberSelector workspaceMemberSelector;

    public QualityCheckService() {
        this(new ZoltTomlParser(), new WorkspaceDiscoveryService(), new WorkspaceMemberSelector());
    }

    QualityCheckService(
            ZoltTomlParser projectParser,
            WorkspaceDiscoveryService workspaceDiscoveryService,
            WorkspaceMemberSelector workspaceMemberSelector) {
        this.projectParser = projectParser;
        this.workspaceDiscoveryService = workspaceDiscoveryService;
        this.workspaceMemberSelector = workspaceMemberSelector;
    }

    public QualityCheckReport check(QualityCheckRequest request) {
        List<String> requestedChecks = requestedChecks(request.checks());
        List<QualityCheckResult> results = new ArrayList<>();
        Path root = request.projectRoot();

        if (request.workspace()) {
            try {
                Optional<Workspace> maybeWorkspace = workspaceDiscoveryService.discover(root);
                if (maybeWorkspace.isEmpty()) {
                    results.add(QualityCheckResult.failed(
                            COMMAND_SURFACE,
                            Optional.empty(),
                            "zolt-workspace.toml",
                            "No Zolt workspace was found for `zolt check --workspace`.",
                            "Run from a workspace root or remove --workspace for a single-project check."));
                    return new QualityCheckReport(root, true, resultsForRequestedChecks(results, requestedChecks));
                }
                Workspace workspace = maybeWorkspace.orElseThrow();
                WorkspaceSelection selection = workspaceMemberSelector.select(workspace, request.workspaceSelection());
                results.add(commandSurfaceWorkspaceResult(workspace, selection));
            } catch (WorkspaceConfigException exception) {
                results.add(QualityCheckResult.failed(
                        COMMAND_SURFACE,
                        Optional.empty(),
                        "zolt-workspace.toml",
                        exception.getMessage(),
                        "Fix zolt-workspace.toml or run `zolt check` for a single project."));
            }
            return new QualityCheckReport(root, true, resultsForRequestedChecks(results, requestedChecks));
        }

        try {
            ProjectConfig config = projectParser.parse(root.resolve("zolt.toml"));
            results.add(commandSurfaceProjectResult(config));
        } catch (ZoltConfigException exception) {
            results.add(QualityCheckResult.failed(
                    COMMAND_SURFACE,
                    Optional.empty(),
                    "zolt.toml",
                    exception.getMessage(),
                    "Fix zolt.toml, then run `zolt check` again."));
        }
        return new QualityCheckReport(root, false, resultsForRequestedChecks(results, requestedChecks));
    }

    public static Set<String> supportedChecks() {
        Set<String> supported = new LinkedHashSet<>(IMPLEMENTED_CHECKS);
        supported.addAll(new TreeMap<>(PLANNED_CHECK_NOTES).keySet());
        return Collections.unmodifiableSet(supported);
    }

    private static QualityCheckResult commandSurfaceProjectResult(ProjectConfig config) {
        return QualityCheckResult.passed(
                COMMAND_SURFACE,
                Optional.empty(),
                config.project().name(),
                "zolt check uses typed Zolt project data; no Maven, Gradle, or shell hooks are run.");
    }

    private static QualityCheckResult commandSurfaceWorkspaceResult(Workspace workspace, WorkspaceSelection selection) {
        return QualityCheckResult.passed(
                COMMAND_SURFACE,
                Optional.empty(),
                workspace.root().getFileName().toString(),
                "zolt check selected "
                        + selection.selectedMembers().size()
                        + " workspace members using typed Zolt workspace data; no Maven, Gradle, or shell hooks are run.");
    }

    private static List<String> requestedChecks(List<String> rawChecks) {
        if (rawChecks.isEmpty()) {
            return List.of(COMMAND_SURFACE);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawCheck : rawChecks) {
            String check = rawCheck == null ? "" : rawCheck.trim();
            if (!check.isEmpty()) {
                normalized.add(check);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<QualityCheckResult> resultsForRequestedChecks(
            List<QualityCheckResult> candidateResults,
            List<String> requestedChecks) {
        Map<String, QualityCheckResult> candidates = new TreeMap<>();
        for (QualityCheckResult result : candidateResults) {
            candidates.put(result.id(), result);
        }

        List<QualityCheckResult> results = new ArrayList<>();
        for (String requestedCheck : requestedChecks) {
            QualityCheckResult result = candidates.get(requestedCheck);
            if (result != null) {
                results.add(result);
            } else if (PLANNED_CHECK_NOTES.containsKey(requestedCheck)) {
                results.add(QualityCheckResult.skipped(
                        requestedCheck,
                        Optional.empty(),
                        requestedCheck,
                        "Quality check `" + requestedCheck + "` is planned but not implemented yet.",
                        "Track " + PLANNED_CHECK_NOTES.get(requestedCheck) + "."));
            } else {
                results.add(QualityCheckResult.failed(
                        "unsupported-check",
                        Optional.empty(),
                        requestedCheck,
                        "Unsupported quality check `" + requestedCheck + "`.",
                        "Use one of: " + String.join(", ", supportedChecks()) + ". Zolt does not run Maven goals, Gradle tasks, shell commands, or arbitrary hooks."));
            }
        }
        return List.copyOf(results);
    }
}
