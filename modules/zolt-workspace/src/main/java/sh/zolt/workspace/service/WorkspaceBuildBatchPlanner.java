package sh.zolt.workspace.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

final class WorkspaceBuildBatchPlanner {
    List<List<String>> batches(Workspace workspace, List<String> includedMembers) {
        Set<String> included = new LinkedHashSet<>(includedMembers);
        Map<String, Integer> memberOrder = memberOrder(includedMembers);
        Map<String, Integer> incomingCounts = new LinkedHashMap<>();
        Map<String, List<String>> dependentsByDependency = new LinkedHashMap<>();
        for (String member : includedMembers) {
            incomingCounts.put(member, 0);
            dependentsByDependency.put(member, new ArrayList<>());
        }

        for (WorkspaceProjectEdge edge : workspace.edges()) {
            if (!included.contains(edge.from()) || !included.contains(edge.to())) {
                continue;
            }
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

        List<List<String>> batches = new ArrayList<>();
        int plannedCount = 0;
        while (!ready.isEmpty()) {
            List<String> batch = new ArrayList<>();
            while (!ready.isEmpty()) {
                batch.add(ready.remove());
            }
            batches.add(List.copyOf(batch));
            plannedCount += batch.size();
            for (String member : batch) {
                for (String dependent : dependentsByDependency.get(member)) {
                    int incoming = incomingCounts.get(dependent) - 1;
                    incomingCounts.put(dependent, incoming);
                    if (incoming == 0) {
                        ready.add(dependent);
                    }
                }
            }
        }

        if (plannedCount != includedMembers.size()) {
            return includedMembers.stream().map(List::of).toList();
        }
        return List.copyOf(batches);
    }

    private static Map<String, Integer> memberOrder(List<String> members) {
        Map<String, Integer> order = new LinkedHashMap<>();
        for (int index = 0; index < members.size(); index++) {
            order.put(members.get(index), index);
        }
        return order;
    }
}
