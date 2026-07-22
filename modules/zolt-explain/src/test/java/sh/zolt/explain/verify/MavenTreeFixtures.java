package sh.zolt.explain.verify;

/**
 * Recorded {@code mvn dependency:tree -DoutputType=text -DoutputFile=...} output committed as
 * fixtures, so the parser and comparator are tested against real Maven serialization without needing
 * Maven at test time. Backslash tree connectors are escaped for Java text blocks.
 */
final class MavenTreeFixtures {
    private MavenTreeFixtures() {
    }

    /** Single-module project: Guava (compile, six transitives) and JUnit Jupiter (test). */
    static String singleModule() {
        return """
                dev.zolt.examples:maven-simple:jar:1.0.0
                +- com.google.guava:guava:jar:33.4.8-jre:compile
                |  +- com.google.guava:failureaccess:jar:1.0.2:compile
                |  +- com.google.guava:listenablefuture:jar:9999.0-empty-to-avoid-conflict-with-guava:compile
                |  +- com.google.code.findbugs:jsr305:jar:3.0.2:compile
                |  +- org.checkerframework:checker-qual:jar:3.43.0:compile
                |  +- com.google.errorprone:error_prone_annotations:jar:2.36.0:compile
                |  \\- com.google.j2objc:j2objc-annotations:jar:3.0.0:compile
                \\- org.junit.jupiter:junit-jupiter:jar:5.11.4:test
                   +- org.junit.jupiter:junit-jupiter-api:jar:5.11.4:test
                   |  +- org.opentest4j:opentest4j:jar:1.3.0:test
                   |  +- org.junit.platform:junit-platform-commons:jar:1.11.4:test
                   |  \\- org.apiguardian:apiguardian-api:jar:1.1.2:test
                   +- org.junit.jupiter:junit-jupiter-params:jar:5.11.4:test
                   \\- org.junit.jupiter:junit-jupiter-engine:jar:5.11.4:test
                      \\- org.junit.platform:junit-platform-engine:jar:1.11.4:test
                """;
    }

    /**
     * Two-module reactor. {@code app} depends on the sibling {@code core}, a runtime classifier
     * artifact (native epoll), a provided servlet API, and JUnit (test). {@code core} is compile-only.
     */
    static String multiModule() {
        return """
                com.example:app:jar:2.1.0
                +- com.example:core:jar:2.1.0:compile
                +- org.slf4j:slf4j-api:jar:2.0.13:compile
                +- io.netty:netty-transport-native-epoll:jar:linux-x86_64:4.1.118.Final:runtime
                +- jakarta.servlet:jakarta.servlet-api:jar:6.0.0:provided
                \\- org.junit.jupiter:junit-jupiter:jar:5.11.4:test
                   \\- org.junit.jupiter:junit-jupiter-api:jar:5.11.4:test
                com.example:core:jar:2.1.0
                +- org.slf4j:slf4j-api:jar:2.0.13:compile
                \\- com.google.guava:guava:jar:33.4.8-jre:compile
                """;
    }

    /** Edge cases: an {@code (optional)} annotation to strip and a {@code system}-scoped artifact. */
    static String edgeCases() {
        return """
                com.example:edge:jar:1.0.0
                +- com.foo:bar:jar:1.2.3:compile (optional)
                +- com.sun:tools:jar:1.8:system
                \\- org.test:only:jar:2.0.0:test
                """;
    }
}
