package com.zolt.ide;

import java.nio.file.Path;
import java.util.List;

public record WorkspaceIdeModel(
        int schemaVersion,
        WorkspaceInfo workspace,
        List<ProjectModel> projects,
        List<ProjectEdge> edges,
        List<IdeModel.Diagnostic> diagnostics) {
    public WorkspaceIdeModel {
        projects = List.copyOf(projects);
        edges = List.copyOf(edges);
        diagnostics = List.copyOf(diagnostics);
    }

    public record WorkspaceInfo(
            String name,
            Path root,
            Path config,
            Path lockfile,
            List<String> members,
            List<String> defaultMembers,
            List<String> buildOrder) {
        public WorkspaceInfo {
            members = List.copyOf(members);
            defaultMembers = List.copyOf(defaultMembers);
            buildOrder = List.copyOf(buildOrder);
        }
    }

    public record ProjectModel(
            String member,
            IdeModel model) {
    }

    public record ProjectEdge(
            String from,
            String to,
            String scope,
            String coordinate,
            boolean exported) {
        public ProjectEdge(
                String from,
                String to,
                String scope,
                String coordinate) {
            this(from, to, scope, coordinate, false);
        }
    }
}
