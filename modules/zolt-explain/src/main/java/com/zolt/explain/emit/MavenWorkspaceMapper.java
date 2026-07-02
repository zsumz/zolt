package com.zolt.explain.emit;

import com.zolt.explain.maven.MavenInspectionResult;
import com.zolt.explain.maven.MavenProjectInspection;
import com.zolt.workspace.WorkspaceConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Maps a multi-module Maven reactor to a {@link DraftWorkspace}: the root aggregator becomes the
 * {@code [workspace]} document; every non-root module becomes a member draft. A dependency whose
 * {@code groupId:artifactId} matches another module is rewritten to {@code { workspace = "<path>" }}.
 */
final class MavenWorkspaceMapper {
    private static final String ROOT_PATH = ".";

    private MavenWorkspaceMapper() {
    }

    /** True when the reactor has real modules to promote into a workspace. */
    static boolean isWorkspace(MavenInspectionResult result) {
        return result.projects().stream().anyMatch(project -> !ROOT_PATH.equals(project.path().toString()));
    }

    static DraftWorkspace map(MavenInspectionResult result) {
        List<MavenProjectInspection> projects = result.projects();
        MavenProjectInspection root = root(projects);

        WorkspaceMemberRegistry registry = new WorkspaceMemberRegistry();
        Map<String, MavenProjectInspection> projectsByCoordinate = new TreeMap<>();
        for (MavenProjectInspection project : projects) {
            String key = memberKey(project);
            registry.register(key, project.path().toString());
            if (key != null) {
                projectsByCoordinate.put(key, project);
            }
        }

        List<String> memberPaths = new ArrayList<>();
        List<DraftWorkspace.Member> members = new ArrayList<>();
        for (MavenProjectInspection project : projects) {
            String path = project.path().toString();
            if (ROOT_PATH.equals(path)) {
                continue;
            }
            memberPaths.add(path);
            members.add(new DraftWorkspace.Member(
                    path,
                    MavenInspectionMapper.mapMember(project, registry, projectsByCoordinate)));
        }

        List<String> notes = new ArrayList<>();
        if (!root.dependencies().isEmpty()) {
            notes.add(
                    "The root aggregator declares " + root.dependencies().size()
                            + " dependency(ies); a [workspace] cannot carry dependencies. Move them into"
                            + " the member(s) that need them, or into a shared module.");
        }
        WorkspaceConfig workspace = new WorkspaceConfig(
                root.artifactId(),
                memberPaths,
                List.copyOf(memberPaths),
                Map.of(),
                Map.of());
        return new DraftWorkspace(workspace, members, notes);
    }

    private static MavenProjectInspection root(List<MavenProjectInspection> projects) {
        return projects.stream()
                .filter(project -> ROOT_PATH.equals(project.path().toString()))
                .findFirst()
                .orElse(projects.get(0));
    }

    private static String memberKey(MavenProjectInspection project) {
        String group = project.groupId();
        String artifact = project.artifactId();
        if (group == null || group.isBlank() || artifact == null || artifact.isBlank()) {
            return null;
        }
        return group + ":" + artifact;
    }
}
