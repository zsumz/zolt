package sh.zolt.workspace.service;

import sh.zolt.workspace.WorkspaceConfigException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkspaceMemberSelector {
    public WorkspaceSelection select(Workspace workspace, WorkspaceSelectionRequest request) {
        if (request.all() && !request.members().isEmpty()) {
            throw new WorkspaceConfigException("Use either --all or member selection for workspace selection, not both.");
        }

        List<String> selectedMembers = selectedMembers(workspace, request);
        Set<String> included = new LinkedHashSet<>();
        Map<String, List<String>> dependenciesByMember = dependenciesByMember(workspace);
        for (String member : selectedMembers) {
            includeDependencies(member, dependenciesByMember, included);
        }

        return new WorkspaceSelection(
                ordered(workspace.buildOrder(), included),
                ordered(workspace.buildOrder(), new LinkedHashSet<>(selectedMembers)));
    }

    private static List<String> selectedMembers(Workspace workspace, WorkspaceSelectionRequest request) {
        if (request.all()) {
            return workspace.buildOrder();
        }
        if (!request.members().isEmpty()) {
            return normalizeRequestedMembers(workspace, request.members());
        }
        if (!workspace.config().defaultMembers().isEmpty()) {
            return normalizeRequestedMembers(workspace, workspace.config().defaultMembers());
        }
        return workspace.buildOrder();
    }

    private static List<String> normalizeRequestedMembers(Workspace workspace, List<String> requestedMembers) {
        Set<String> knownMembers = new LinkedHashSet<>(workspace.members().stream()
                .map(WorkspaceMember::path)
                .toList());
        Set<String> normalizedMembers = new LinkedHashSet<>();
        for (String requestedMember : requestedMembers) {
            String normalizedMember = normalizeMemberPath(requestedMember);
            if (!knownMembers.contains(normalizedMember)) {
                throw new WorkspaceConfigException(
                        "Workspace member `"
                                + normalizedMember
                                + "` is not declared in [workspace].members. Choose a declared member or use --all.");
            }
            normalizedMembers.add(normalizedMember);
        }
        return List.copyOf(normalizedMembers);
    }

    private static String normalizeMemberPath(String requestedMember) {
        Path configured = Path.of(requestedMember);
        Path normalized = configured.normalize();
        if (requestedMember.isBlank()
                || configured.isAbsolute()
                || normalized.startsWith("..")) {
            throw new WorkspaceConfigException(
                    "Invalid workspace member `"
                            + requestedMember
                            + "`. Use a relative member path declared in [workspace].members.");
        }
        String normalizedPath = normalized.toString().replace('\\', '/');
        return normalizedPath.isBlank() ? "." : normalizedPath;
    }

    private static Map<String, List<String>> dependenciesByMember(Workspace workspace) {
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            dependencies.put(member.path(), new ArrayList<>());
        }
        for (WorkspaceProjectEdge edge : workspace.edges()) {
            dependencies.get(edge.from()).add(edge.to());
        }
        return dependencies;
    }

    private static void includeDependencies(
            String member,
            Map<String, List<String>> dependenciesByMember,
            Set<String> included) {
        if (!included.add(member)) {
            return;
        }
        for (String dependency : dependenciesByMember.get(member)) {
            includeDependencies(dependency, dependenciesByMember, included);
        }
    }

    private static List<String> ordered(List<String> buildOrder, Set<String> members) {
        return buildOrder.stream()
                .filter(members::contains)
                .toList();
    }
}
