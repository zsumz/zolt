package com.zolt.workspace;

public record WorkspaceProjectEdge(
        String from,
        String to,
        String scope,
        String coordinate,
        boolean exported) {
    public WorkspaceProjectEdge(
            String from,
            String to,
            String scope,
            String coordinate) {
        this(from, to, scope, coordinate, false);
    }
}
