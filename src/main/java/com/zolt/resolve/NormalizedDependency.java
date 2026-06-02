package com.zolt.resolve;

import com.zolt.maven.RawPomDependency;

public record NormalizedDependency(
        RawPomDependency rawDependency,
        DependencyScope scope) {
}
