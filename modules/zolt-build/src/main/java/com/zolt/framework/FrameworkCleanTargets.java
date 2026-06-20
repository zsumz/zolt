package com.zolt.framework;

import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public final class FrameworkCleanTargets {
    public Set<Path> cleanTargets(Path projectRoot, ProjectConfig config) {
        LinkedHashSet<Path> targets = new LinkedHashSet<>();
        if (config.frameworkSettings().springBoot().nativeEnabled()) {
            targets.add(projectRoot.resolve(config.build().outputRoot()).resolve("spring-aot").normalize());
        }
        if (config.frameworkSettings().quarkus().enabled()) {
            targets.add(projectRoot.resolve(config.build().outputRoot()).resolve("quarkus").normalize());
            targets.add(projectRoot.resolve(config.build().outputRoot()).resolve("quarkus-app").normalize());
        }
        return targets;
    }
}
