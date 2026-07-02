package sh.zolt.workspace.discovery;

import sh.zolt.workspace.WorkspaceConfigException;
import sh.zolt.workspace.service.WorkspaceMember;
import sh.zolt.workspace.service.WorkspaceProjectEdge;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

final class WorkspaceBuildOrderPlanner {
    List<String> buildOrder(List<WorkspaceMember> members, List<WorkspaceProjectEdge> edges) {
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

    private enum VisitState {
        VISITING,
        VISITED
    }
}
