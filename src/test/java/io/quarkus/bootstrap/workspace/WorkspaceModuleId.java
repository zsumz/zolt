package io.quarkus.bootstrap.workspace;

public record WorkspaceModuleId(
        String groupId,
        String artifactId,
        String version) {
    public static WorkspaceModuleId of(String groupId, String artifactId, String version) {
        return new WorkspaceModuleId(groupId, artifactId, version);
    }
}
