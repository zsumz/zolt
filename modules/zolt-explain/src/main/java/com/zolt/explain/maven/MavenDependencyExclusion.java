package com.zolt.explain.maven;

/**
 * A single {@code <exclusion>} pruned from a Maven dependency's transitive graph. Group and artifact
 * are captured verbatim (after {@code ${...}} interpolation) and may be the Maven wildcard {@code *}
 * when the POM omits one of them.
 */
public record MavenDependencyExclusion(String groupId, String artifactId) {}
