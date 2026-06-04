package com.zolt.workspace;

import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

public final class WorkspaceDiscoveryService {
    private final WorkspaceConfigParser workspaceParser;
    private final ZoltTomlParser projectParser;

    public WorkspaceDiscoveryService() {
        this(new WorkspaceConfigParser(), new ZoltTomlParser());
    }

    WorkspaceDiscoveryService(
            WorkspaceConfigParser workspaceParser,
            ZoltTomlParser projectParser) {
        this.workspaceParser = workspaceParser;
        this.projectParser = projectParser;
    }

    public Optional<Workspace> discover(Path startDirectory) {
        Path current = startDirectory.toAbsolutePath().normalize();
        if (Files.isRegularFile(current)) {
            current = current.getParent();
        }

        while (current != null) {
            if (Files.isRegularFile(current.resolve(WorkspaceConfigParser.WORKSPACE_FILE))) {
                return Optional.of(load(current));
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    public Workspace load(Path workspaceRoot) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path configPath = root.resolve(WorkspaceConfigParser.WORKSPACE_FILE).normalize();
        WorkspaceConfig config = workspaceParser.parse(configPath);
        List<WorkspaceMember> members = members(root, config);
        validateDefaultMembers(root, config.defaultMembers(), members);
        List<WorkspaceProjectEdge> edges = workspaceProjectEdges(root, members);
        List<String> buildOrder = buildOrder(members, edges);
        return new Workspace(root, configPath, config, members, edges, buildOrder);
    }

    private List<WorkspaceMember> members(Path root, WorkspaceConfig config) {
        List<WorkspaceMember> members = new ArrayList<>();
        Set<String> memberPaths = new LinkedHashSet<>();
        Map<String, String> coordinates = new LinkedHashMap<>();
        for (String declaredPath : config.members()) {
            ResolvedMemberPath resolved = resolveMemberPath(root, declaredPath, "[workspace].members");
            if (!memberPaths.add(resolved.path())) {
                throw new WorkspaceConfigException(
                        "Duplicate workspace member `" + resolved.path() + "` after path normalization.");
            }

            Path projectConfigPath = resolved.directory().resolve("zolt.toml");
            if (!Files.isRegularFile(projectConfigPath)) {
                throw new WorkspaceConfigException(
                        "Workspace member `"
                                + resolved.path()
                                + "` must contain zolt.toml at "
                                + projectConfigPath
                                + ".");
            }

            ProjectConfig projectConfig = parseProjectConfig(resolved.path(), projectConfigPath);
            String coordinate = projectConfig.project().group() + ":" + projectConfig.project().name();
            String existingMember = coordinates.putIfAbsent(coordinate, resolved.path());
            if (existingMember != null) {
                throw new WorkspaceConfigException(
                        "Workspace member coordinate `"
                                + coordinate
                                + "` is used by both `"
                                + existingMember
                                + "` and `"
                                + resolved.path()
                                + "`. Give each member a unique [project].group:[project].name.");
            }
            members.add(new WorkspaceMember(resolved.path(), resolved.directory(), projectConfig));
        }
        return List.copyOf(members);
    }

    private ProjectConfig parseProjectConfig(String memberPath, Path projectConfigPath) {
        try {
            return projectParser.parse(projectConfigPath);
        } catch (ZoltConfigException exception) {
            throw new WorkspaceConfigException(
                    "Workspace member `" + memberPath + "` has an invalid zolt.toml. " + exception.getMessage());
        }
    }

    private void validateDefaultMembers(
            Path root,
            List<String> defaultMembers,
            List<WorkspaceMember> members) {
        Set<String> memberPaths = new LinkedHashSet<>();
        for (WorkspaceMember member : members) {
            memberPaths.add(member.path());
        }
        Set<String> normalizedDefaults = new LinkedHashSet<>();
        for (String declaredPath : defaultMembers) {
            ResolvedMemberPath resolved = resolveMemberPath(root, declaredPath, "[workspace].defaultMembers");
            if (!normalizedDefaults.add(resolved.path())) {
                throw new WorkspaceConfigException(
                        "Duplicate workspace default member `" + resolved.path() + "` after path normalization.");
            }
            if (!memberPaths.contains(resolved.path())) {
                throw new WorkspaceConfigException(
                        "Workspace default member `"
                                + resolved.path()
                                + "` must also be listed in [workspace].members.");
            }
        }
    }

    private List<WorkspaceProjectEdge> workspaceProjectEdges(Path root, List<WorkspaceMember> members) {
        Map<String, WorkspaceMember> membersByPath = new LinkedHashMap<>();
        for (WorkspaceMember member : members) {
            membersByPath.put(member.path(), member);
        }

        List<WorkspaceProjectEdge> edges = new ArrayList<>();
        for (WorkspaceMember member : members) {
            addWorkspaceProjectEdges(
                    root,
                    membersByPath,
                    edges,
                    member,
                    "compile",
                    member.config().workspaceApiDependencies(),
                    "[api.dependencies]",
                    true);
            addWorkspaceProjectEdges(
                    root,
                    membersByPath,
                    edges,
                    member,
                    "compile",
                    member.config().workspaceDependencies(),
                    "[dependencies]",
                    false);
            addWorkspaceProjectEdges(
                    root,
                    membersByPath,
                    edges,
                    member,
                    "test",
                    member.config().workspaceTestDependencies(),
                    "[test.dependencies]",
                    false);
        }
        return List.copyOf(edges);
    }

    private static void addWorkspaceProjectEdges(
            Path root,
            Map<String, WorkspaceMember> membersByPath,
            List<WorkspaceProjectEdge> edges,
            WorkspaceMember from,
            String scope,
            Map<String, String> dependencies,
            String section,
            boolean exported) {
        for (Map.Entry<String, String> dependency : new TreeMap<>(dependencies).entrySet()) {
            String coordinate = dependency.getKey();
            String declaredPath = dependency.getValue();
            ResolvedMemberPath resolved = resolveMemberPath(root, declaredPath, section + "." + coordinate + ".workspace");
            WorkspaceMember targetByPath = membersByPath.get(resolved.path());
            if (targetByPath == null) {
                throw new WorkspaceConfigException(
                        "Workspace dependency `"
                                + coordinate
                                + "` in member `"
                                + from.path()
                                + "` points to `"
                                + resolved.path()
                                + "`, but that path is not listed in [workspace].members.");
            }
            if (from.path().equals(targetByPath.path())) {
                throw new WorkspaceConfigException(
                        "Workspace member `"
                                + from.path()
                                + "` cannot depend on itself through `"
                                + coordinate
                                + "`.");
            }
            String targetCoordinate = coordinate(targetByPath);
            if (!targetCoordinate.equals(coordinate)) {
                throw new WorkspaceConfigException(
                        "Workspace dependency `"
                                + coordinate
                                + "` in member `"
                                + from.path()
                                + "` points to `"
                                + resolved.path()
                                + "`, whose project coordinate is `"
                                + targetCoordinate
                                + "`. Update the dependency key or workspace path so they match.");
            }
            edges.add(new WorkspaceProjectEdge(from.path(), targetByPath.path(), scope, coordinate, exported));
        }
    }

    private static String coordinate(WorkspaceMember member) {
        return member.config().project().group() + ":" + member.config().project().name();
    }

    private static List<String> buildOrder(List<WorkspaceMember> members, List<WorkspaceProjectEdge> edges) {
        Map<String, Integer> memberOrder = memberOrder(members);
        Map<String, Integer> incomingCounts = new LinkedHashMap<>();
        Map<String, List<String>> dependentsByDependency = new LinkedHashMap<>();
        for (WorkspaceMember member : members) {
            incomingCounts.put(member.path(), 0);
            dependentsByDependency.put(member.path(), new ArrayList<>());
        }

        for (WorkspaceProjectEdge edge : edges) {
            incomingCounts.put(edge.from(), incomingCounts.get(edge.from()) + 1);
            dependentsByDependency.get(edge.to()).add(edge.from());
        }

        PriorityQueue<String> ready = new PriorityQueue<>(
                (left, right) -> Integer.compare(memberOrder.get(left), memberOrder.get(right)));
        for (Map.Entry<String, Integer> entry : incomingCounts.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        List<String> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String member = ready.remove();
            ordered.add(member);
            for (String dependent : dependentsByDependency.get(member)) {
                int incoming = incomingCounts.get(dependent) - 1;
                incomingCounts.put(dependent, incoming);
                if (incoming == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (ordered.size() != members.size()) {
            throw new WorkspaceConfigException(
                    "Workspace dependency cycle detected: " + String.join(" -> ", cyclePath(members, edges)) + ".");
        }
        return List.copyOf(ordered);
    }

    private static Map<String, Integer> memberOrder(List<WorkspaceMember> members) {
        Map<String, Integer> order = new LinkedHashMap<>();
        for (int index = 0; index < members.size(); index++) {
            order.put(members.get(index).path(), index);
        }
        return order;
    }

    private static List<String> cyclePath(List<WorkspaceMember> members, List<WorkspaceProjectEdge> edges) {
        Map<String, Integer> memberOrder = memberOrder(members);
        Map<String, List<String>> dependenciesByMember = new LinkedHashMap<>();
        for (WorkspaceMember member : members) {
            dependenciesByMember.put(member.path(), new ArrayList<>());
        }
        for (WorkspaceProjectEdge edge : edges) {
            dependenciesByMember.get(edge.from()).add(edge.to());
        }
        for (List<String> dependencies : dependenciesByMember.values()) {
            dependencies.sort((left, right) -> Integer.compare(memberOrder.get(left), memberOrder.get(right)));
        }

        Map<String, VisitState> states = new LinkedHashMap<>();
        ArrayList<String> stack = new ArrayList<>();
        for (WorkspaceMember member : members) {
            List<String> cycle = findCycle(member.path(), dependenciesByMember, states, stack);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of("<unknown>");
    }

    private static List<String> findCycle(
            String member,
            Map<String, List<String>> dependenciesByMember,
            Map<String, VisitState> states,
            ArrayList<String> stack) {
        VisitState state = states.get(member);
        if (state == VisitState.VISITING) {
            int start = stack.indexOf(member);
            ArrayList<String> cycle = new ArrayList<>(stack.subList(start, stack.size()));
            cycle.add(member);
            return List.copyOf(cycle);
        }
        if (state == VisitState.VISITED) {
            return List.of();
        }

        states.put(member, VisitState.VISITING);
        stack.add(member);
        for (String dependency : dependenciesByMember.get(member)) {
            List<String> cycle = findCycle(dependency, dependenciesByMember, states, stack);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        stack.remove(stack.size() - 1);
        states.put(member, VisitState.VISITED);
        return List.of();
    }

    private static ResolvedMemberPath resolveMemberPath(Path root, String declaredPath, String field) {
        Path configured = Path.of(declaredPath);
        Path directory = root.resolve(configured).normalize();
        if (configured.isAbsolute() || !directory.startsWith(root)) {
            throw new WorkspaceConfigException(
                    "Invalid workspace member path `"
                            + declaredPath
                            + "` in "
                            + field
                            + ". Use a relative path under the workspace root.");
        }
        Path relative = root.relativize(directory);
        String normalizedPath = relative.toString().replace('\\', '/');
        if (normalizedPath.isBlank()) {
            normalizedPath = ".";
        }
        return new ResolvedMemberPath(normalizedPath, directory);
    }

    private record ResolvedMemberPath(
            String path,
            Path directory) {
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
