package sh.zolt.explain.verify;

/**
 * Recorded {@code gradle -q <path>:dependencies} output committed as fixtures, so the Gradle tree
 * parser is tested against real Gradle serialization without needing Gradle at test time. Lines are
 * exact (leading spaces literal, {@code \\---} connectors escaped); each fixture was captured from a
 * real Gradle 9 run and trimmed to the sections that matter for the resolved-set comparison.
 */
final class GradleTreeFixtures {
    private GradleTreeFixtures() {
    }

    private static String join(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    /**
     * Single-project build (Guava on compile/runtime, JUnit Jupiter on test). Exercises the JUnit BOM
     * platform node (all-{@code (c)} children), {@code (c)} constraints, and {@code (*)} repeats.
     */
    static String singleProject() {
        return join(
                "",
                "------------------------------------------------------------",
                "Root project 'gradle-simple'",
                "------------------------------------------------------------",
                "",
                "annotationProcessor - Annotation processors and their dependencies for source set 'main'.",
                "No dependencies",
                "",
                "compileClasspath - Compile classpath for source set 'main'.",
                "\\--- com.google.guava:guava:33.4.8-jre",
                "     +--- com.google.guava:failureaccess:1.0.3",
                "     +--- com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
                "     +--- org.jspecify:jspecify:1.0.0",
                "     +--- com.google.errorprone:error_prone_annotations:2.36.0",
                "     \\--- com.google.j2objc:j2objc-annotations:3.0.0",
                "",
                "compileOnly - Compile-only dependencies for the 'main' feature. (n)",
                "No dependencies",
                "",
                "implementation - Implementation dependencies for the 'main' feature. (n)",
                "\\--- com.google.guava:guava:33.4.8-jre (n)",
                "",
                "runtimeClasspath - Runtime classpath of source set 'main'.",
                "\\--- com.google.guava:guava:33.4.8-jre",
                "     +--- com.google.guava:failureaccess:1.0.3",
                "     +--- com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
                "     +--- org.jspecify:jspecify:1.0.0",
                "     +--- com.google.errorprone:error_prone_annotations:2.36.0",
                "     \\--- com.google.j2objc:j2objc-annotations:3.0.0",
                "",
                "testRuntimeClasspath - Runtime classpath of source set 'test'.",
                "+--- com.google.guava:guava:33.4.8-jre",
                "|    +--- com.google.guava:failureaccess:1.0.3",
                "|    +--- com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
                "|    +--- org.jspecify:jspecify:1.0.0",
                "|    +--- com.google.errorprone:error_prone_annotations:2.36.0",
                "|    \\--- com.google.j2objc:j2objc-annotations:3.0.0",
                "\\--- org.junit.jupiter:junit-jupiter:5.11.4",
                "     +--- org.junit:junit-bom:5.11.4",
                "     |    +--- org.junit.jupiter:junit-jupiter:5.11.4 (c)",
                "     |    +--- org.junit.jupiter:junit-jupiter-api:5.11.4 (c)",
                "     |    +--- org.junit.jupiter:junit-jupiter-engine:5.11.4 (c)",
                "     |    +--- org.junit.jupiter:junit-jupiter-params:5.11.4 (c)",
                "     |    +--- org.junit.platform:junit-platform-commons:1.11.4 (c)",
                "     |    \\--- org.junit.platform:junit-platform-engine:1.11.4 (c)",
                "     +--- org.junit.jupiter:junit-jupiter-api:5.11.4",
                "     |    +--- org.junit:junit-bom:5.11.4 (*)",
                "     |    +--- org.opentest4j:opentest4j:1.3.0",
                "     |    \\--- org.junit.platform:junit-platform-commons:1.11.4",
                "     |         \\--- org.junit:junit-bom:5.11.4 (*)",
                "     +--- org.junit.jupiter:junit-jupiter-params:5.11.4",
                "     |    +--- org.junit:junit-bom:5.11.4 (*)",
                "     |    \\--- org.junit.jupiter:junit-jupiter-api:5.11.4 (*)",
                "     \\--- org.junit.jupiter:junit-jupiter-engine:5.11.4",
                "          +--- org.junit:junit-bom:5.11.4 (*)",
                "          +--- org.junit.platform:junit-platform-engine:1.11.4",
                "          |    +--- org.junit:junit-bom:5.11.4 (*)",
                "          |    +--- org.opentest4j:opentest4j:1.3.0",
                "          |    \\--- org.junit.platform:junit-platform-commons:1.11.4 (*)",
                "          \\--- org.junit.jupiter:junit-jupiter-api:5.11.4 (*)",
                "",
                "(c) - A dependency constraint, not a dependency. The dependency affected by the constraint occurs elsewhere in the tree.",
                "(*) - Indicates repeated occurrences of a transitive dependency subtree.",
                "",
                "(n) - A dependency or dependency configuration that cannot be resolved.",
                "",
                "A web-based, searchable dependency report is available by adding the --scan option.");
    }

    /**
     * Two-module build in one combined invocation: {@code :app} depends on {@code project :lib} plus
     * Guava (compile) and a {@code compileOnly} Lombok, {@code :lib} exposes commons-lang3. Exercises
     * multi-block parsing, {@code project :x} identity mapping, the compileOnly note, and BOM exclusion.
     */
    static String multiProject() {
        return join(
                "",
                "------------------------------------------------------------",
                "Root project 'demo-mp'",
                "------------------------------------------------------------",
                "",
                "compileClasspath - Compile classpath for source set 'main'.",
                "No dependencies",
                "",
                "------------------------------------------------------------",
                "Project ':app'",
                "------------------------------------------------------------",
                "",
                "compileClasspath - Compile classpath for source set 'main'.",
                "+--- org.projectlombok:lombok:1.18.34",
                "+--- project :lib",
                "|    \\--- org.apache.commons:commons-lang3:3.17.0",
                "\\--- com.google.guava:guava:33.4.8-jre",
                "     +--- com.google.guava:failureaccess:1.0.3",
                "     +--- org.jspecify:jspecify:1.0.0",
                "     \\--- com.google.j2objc:j2objc-annotations:3.0.0",
                "",
                "compileOnly - Compile-only dependencies for the 'main' feature. (n)",
                "\\--- org.projectlombok:lombok:1.18.34 (n)",
                "",
                "implementation - Implementation dependencies for the 'main' feature. (n)",
                "+--- project lib (n)",
                "\\--- com.google.guava:guava:33.4.8-jre (n)",
                "",
                "runtimeClasspath - Runtime classpath of source set 'main'.",
                "+--- project :lib",
                "|    +--- org.apache.commons:commons-lang3:3.17.0",
                "|    \\--- org.slf4j:slf4j-api:2.0.13",
                "\\--- com.google.guava:guava:33.4.8-jre",
                "     +--- com.google.guava:failureaccess:1.0.3",
                "     +--- org.jspecify:jspecify:1.0.0",
                "     \\--- com.google.j2objc:j2objc-annotations:3.0.0",
                "",
                "testRuntimeClasspath - Runtime classpath of source set 'test'.",
                "+--- project :lib",
                "|    +--- org.apache.commons:commons-lang3:3.17.0",
                "|    \\--- org.slf4j:slf4j-api:2.0.13",
                "+--- com.google.guava:guava:33.4.8-jre",
                "|    +--- com.google.guava:failureaccess:1.0.3",
                "|    +--- org.jspecify:jspecify:1.0.0",
                "|    \\--- com.google.j2objc:j2objc-annotations:3.0.0",
                "\\--- org.junit.jupiter:junit-jupiter:5.11.4",
                "     +--- org.junit:junit-bom:5.11.4",
                "     |    +--- org.junit.jupiter:junit-jupiter:5.11.4 (c)",
                "     |    \\--- org.junit.jupiter:junit-jupiter-api:5.11.4 (c)",
                "     \\--- org.junit.jupiter:junit-jupiter-api:5.11.4",
                "          +--- org.junit:junit-bom:5.11.4 (*)",
                "          \\--- org.opentest4j:opentest4j:1.3.0",
                "",
                "------------------------------------------------------------",
                "Project ':lib'",
                "------------------------------------------------------------",
                "",
                "compileClasspath - Compile classpath for source set 'main'.",
                "+--- org.apache.commons:commons-lang3:3.17.0",
                "\\--- org.slf4j:slf4j-api:2.0.13",
                "",
                "runtimeClasspath - Runtime classpath of source set 'main'.",
                "+--- org.apache.commons:commons-lang3:3.17.0",
                "\\--- org.slf4j:slf4j-api:2.0.13",
                "",
                "(c) - A dependency constraint, not a dependency. The dependency affected by the constraint occurs elsewhere in the tree.",
                "(*) - Indicates repeated occurrences of a transitive dependency subtree.",
                "",
                "A web-based, searchable dependency report is available by adding the --scan option.");
    }

    /**
     * Conflict resolution: a directly requested slf4j-api {@code 1.7.30} is bumped to {@code 2.0.13} by
     * a transitive, printed as {@code 1.7.30 -> 2.0.13}. The resolved right-hand side must win.
     */
    static String conflict() {
        return join(
                "",
                "------------------------------------------------------------",
                "Root project 'conflict-demo'",
                "------------------------------------------------------------",
                "",
                "compileClasspath - Compile classpath for source set 'main'.",
                "+--- org.slf4j:slf4j-api:1.7.30 -> 2.0.13",
                "\\--- ch.qos.logback:logback-classic:1.5.6",
                "     +--- ch.qos.logback:logback-core:1.5.6",
                "     \\--- org.slf4j:slf4j-api:2.0.13",
                "",
                "runtimeClasspath - Runtime classpath of source set 'main'.",
                "+--- org.slf4j:slf4j-api:1.7.30 -> 2.0.13",
                "\\--- ch.qos.logback:logback-classic:1.5.6",
                "     +--- ch.qos.logback:logback-core:1.5.6",
                "     \\--- org.slf4j:slf4j-api:2.0.13",
                "",
                "(n) - A dependency or dependency configuration that cannot be resolved.",
                "",
                "A web-based, searchable dependency report is available by adding the --scan option.");
    }
}
