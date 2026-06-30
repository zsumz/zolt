package com.zolt.explain.emit;

import com.zolt.explain.gradle.GradleInspectionResult;
import com.zolt.explain.maven.MavenInspectionResult;

/**
 * Converts a static Maven or Gradle migration audit into a {@link DraftZoltToml}.
 *
 * <p>Maven scopes map to Zolt dependency sections (compile -&gt; dependencies, runtime -&gt; runtime,
 * provided -&gt; provided, test -&gt; test) and imported BOMs map to platforms. Gradle configurations
 * map analogously. Anything the audit cannot map safely (multi-module reactors, version-catalog
 * aliases without a resolved coordinate, unresolved notations, Maven profiles, custom repositories,
 * unknown scopes) is returned as a review note rather than guessed into the config.
 */
public final class InspectionToProjectConfig {
    public DraftZoltToml fromMaven(MavenInspectionResult result) {
        if (result.projects().isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot emit a draft zolt.toml: the Maven audit found no project. "
                            + "Run zolt explain from a Maven project root.");
        }
        return MavenInspectionMapper.map(result);
    }

    public DraftZoltToml fromGradle(GradleInspectionResult result) {
        if (result.projects().isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot emit a draft zolt.toml: the Gradle audit found no project. "
                            + "Run zolt explain from a Gradle project root.");
        }
        return GradleInspectionMapper.map(result);
    }
}
