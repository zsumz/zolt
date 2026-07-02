package sh.zolt.resolve.framework;

import sh.zolt.resolve.request.DependencyRequest;
import java.util.List;

@FunctionalInterface
public interface FrameworkDependencyRequestPlanner {
    List<DependencyRequest> plan(FrameworkDependencyRequestPlanRequest request);

    static FrameworkDependencyRequestPlanner none() {
        return request -> List.of();
    }
}
