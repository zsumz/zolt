package com.zolt.resolve;

import com.zolt.project.ProjectConfig;
import com.zolt.quarkus.QuarkusDependencyRequestPlanner;
import java.util.Map;

abstract class ResolveServiceQuarkusTestSupport extends ResolveServiceTestSupport {
    @Override
    ResolveService createResolveService() {
        return new ResolveService(new QuarkusDependencyRequestPlanner());
    }

    ProjectConfig quarkusConfigWithDependencies(Map<String, String> dependencies) {
        return ResolveFeatureTestConfigs.quarkusConfigWithDependencies(baseUri, dependencies);
    }

    ProjectConfig quarkusPlatformConfigWithDependencies(Map<String, String> dependencies) {
        return ResolveFeatureTestConfigs.quarkusPlatformConfigWithDependencies(baseUri, dependencies);
    }
}
