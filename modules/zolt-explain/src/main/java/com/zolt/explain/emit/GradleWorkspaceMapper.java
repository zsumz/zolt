package com.zolt.explain.emit;

import com.zolt.explain.gradle.GradleInspectionResult;
import com.zolt.explain.gradle.GradleProjectInspection;
import com.zolt.workspace.WorkspaceConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps a Gradle multi-project build to a {@link DraftWorkspace}: the root project becomes the
 * {@code [workspace]} document; every included subproject becomes a member draft. A
 * {@code project(":lib")} edge is rewritten to {@code { workspace = "lib" }}.
 */
final class GradleWorkspaceMapper {
    private static final String ROOT_PATH = ".";

    private GradleWorkspaceMapper() {
    }

    /** True when the build includes subprojects to promote into a workspace. */
    static boolean isWorkspace(GradleInspectionResult result) {
        return result.projects().stream().anyMatch(project -> !ROOT_PATH.equals(project.path().toString()));
    }

    static DraftWorkspace map(GradleInspectionResult result) {
        List<GradleProjectInspection> projects = result.projects();
        GradleProjectInspection root = root(projects);

        WorkspaceMemberRegistry registry = new WorkspaceMemberRegistry();
        for (GradleProjectInspection project : projects) {
            String path = project.path().toString();
            if (!ROOT_PATH.equals(path)) {
                registry.register(path, path);
            }
        }

        List<String> memberPaths = new ArrayList<>();
        List<DraftWorkspace.Member> members = new ArrayList<>();
        for (GradleProjectInspection project : projects) {
            String path = project.path().toString();
            if (ROOT_PATH.equals(path)) {
                continue;
            }
            memberPaths.add(path);
            members.add(new DraftWorkspace.Member(
                    path,
                    GradleInspectionMapper.mapMember(project, registry, result.versionCatalogAliases())));
        }

        List<String> notes = new ArrayList<>();
        notes.addAll(GradleInspectionMapper.skippedIncludedProjectNotes(result));
        if (!root.dependencies().isEmpty()) {
            notes.add(
                    "The root project declares " + root.dependencies().size()
                            + " dependency(ies); a [workspace] cannot carry dependencies. Move them into"
                            + " the member(s) that need them, or into a shared module.");
        }
        WorkspaceConfig workspace = new WorkspaceConfig(
                root.name(),
                memberPaths,
                List.copyOf(memberPaths),
                Map.of(),
                Map.of());
        return new DraftWorkspace(workspace, members, notes);
    }

    private static GradleProjectInspection root(List<GradleProjectInspection> projects) {
        return projects.stream()
                .filter(project -> ROOT_PATH.equals(project.path().toString()))
                .findFirst()
                .orElse(projects.get(0));
    }
}
