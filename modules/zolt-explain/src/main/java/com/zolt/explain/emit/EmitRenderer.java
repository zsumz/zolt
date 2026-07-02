package com.zolt.explain.emit;

import com.zolt.explain.gradle.GradleInspectionResult;
import com.zolt.explain.maven.MavenInspectionResult;
import java.util.List;

/**
 * Turns a Maven or Gradle audit into a ready-to-print draft: a single {@code zolt.toml} for a
 * single-project build, or a multi-document workspace bundle (root {@code [workspace]} plus one
 * labelled member document per module) for a reactor / multi-project build.
 *
 * <p>Groups the mapping and rendering collaborators behind one entry point so callers depend on a
 * single seam. The {@link ProjectConfigRenderer} and {@link WorkspaceConfigRenderer} are injected by
 * the CLI (they wrap the real zolt-toml writers), keeping zolt-explain free of a zolt-toml dependency.
 */
public final class EmitRenderer {
    private final InspectionToProjectConfig mapper;
    private final DraftZoltTomlRenderer draftRenderer;
    private final DraftWorkspaceRenderer workspaceRenderer;
    private final ProjectConfigRenderer configRenderer;
    private final WorkspaceConfigRenderer workspaceConfigRenderer;

    public EmitRenderer(
            InspectionToProjectConfig mapper,
            DraftZoltTomlRenderer draftRenderer,
            DraftWorkspaceRenderer workspaceRenderer,
            ProjectConfigRenderer configRenderer,
            WorkspaceConfigRenderer workspaceConfigRenderer) {
        this.mapper = mapper;
        this.draftRenderer = draftRenderer;
        this.workspaceRenderer = workspaceRenderer;
        this.configRenderer = configRenderer;
        this.workspaceConfigRenderer = workspaceConfigRenderer;
    }

    public String renderMaven(MavenInspectionResult result) {
        return render(mapper.emitFromMaven(result));
    }

    public String renderGradle(GradleInspectionResult result) {
        return render(mapper.emitFromGradle(result));
    }

    public List<DraftZoltTomlDocument> renderMavenDocuments(MavenInspectionResult result) {
        return renderDocuments(mapper.emitFromMaven(result));
    }

    public List<DraftZoltTomlDocument> renderGradleDocuments(GradleInspectionResult result) {
        return renderDocuments(mapper.emitFromGradle(result));
    }

    private String render(DraftEmit emit) {
        if (emit instanceof DraftWorkspace workspace) {
            return workspaceRenderer.render(workspace, workspaceConfigRenderer, configRenderer);
        }
        return draftRenderer.render((DraftZoltToml) emit, configRenderer);
    }

    private List<DraftZoltTomlDocument> renderDocuments(DraftEmit emit) {
        if (emit instanceof DraftWorkspace workspace) {
            return workspaceRenderer.renderDocuments(workspace, workspaceConfigRenderer, configRenderer);
        }
        return List.of(new DraftZoltTomlDocument(
                "zolt.toml",
                withTrailingNewline(draftRenderer.render((DraftZoltToml) emit, configRenderer))));
    }

    private static String withTrailingNewline(String value) {
        return value.stripTrailing() + "\n";
    }
}
