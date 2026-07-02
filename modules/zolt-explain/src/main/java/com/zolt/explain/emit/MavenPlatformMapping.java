package com.zolt.explain.emit;

import com.zolt.explain.maven.MavenDependencyInspection;
import java.util.List;
import java.util.Map;

record MavenPlatformMapping(
        Map<String, String> platforms,
        Map<String, String> managedPins,
        List<MavenDependencyInspection> managedDependencies) {
    MavenPlatformMapping {
        platforms = Map.copyOf(platforms);
        managedPins = Map.copyOf(managedPins);
        managedDependencies = List.copyOf(managedDependencies);
    }
}
