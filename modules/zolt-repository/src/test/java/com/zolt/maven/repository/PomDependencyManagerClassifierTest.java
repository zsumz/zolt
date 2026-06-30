package com.zolt.maven.repository;

import static com.zolt.maven.repository.PomDependencyManagerTestSupport.effective;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PomDependencyManagerClassifierTest {
    private final RawPomParser parser = new RawPomParser();
    private final PomDependencyManager manager = new PomDependencyManager();

    @Test
    void classifierManagedDependencyWithUnresolvedPropertyIsIgnored() {
        EffectiveRawPom pom = effective(parser, """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>io.netty</groupId>
                        <artifactId>netty-transport-native-epoll</artifactId>
                        <version>4.2.12.Final</version>
                        <classifier>${os.detected.classifier}</classifier>
                      </dependency>
                      <dependency>
                        <groupId>org.slf4j</groupId>
                        <artifactId>slf4j-api</artifactId>
                        <version>2.0.17</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        RawPomDependency dependency = manager.applyManagedVersions(pom).getFirst();

        assertEquals("2.0.17", dependency.version().orElseThrow());
    }

    @Test
    void fixedClassifierDependencyMissingVersionInheritsManagedVersion() {
        EffectiveRawPom pom = effective(parser, """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.google.inject</groupId>
                        <artifactId>guice</artifactId>
                        <version>5.1.0</version>
                        <classifier>classes</classifier>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.inject</groupId>
                      <artifactId>guice</artifactId>
                      <classifier>classes</classifier>
                    </dependency>
                  </dependencies>
                </project>
                """);

        RawPomDependency dependency = manager.applyManagedVersions(pom).getFirst();

        assertEquals("5.1.0", dependency.version().orElseThrow());
        assertEquals("classes", dependency.classifier().orElseThrow());
    }

    @Test
    void classifierDependenciesFailWithActionableDiagnostic() {
        EffectiveRawPom pom = effective(parser, """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>io.netty</groupId>
                      <artifactId>netty-transport-native-epoll</artifactId>
                      <version>4.2.12.Final</version>
                      <classifier>${os.detected.classifier}</classifier>
                    </dependency>
                  </dependencies>
                </project>
                """);

        PomInterpolationException exception = assertThrows(
                PomInterpolationException.class,
                () -> manager.applyManagedVersions(pom));

        assertTrue(exception.getMessage().contains("Dynamic classifier selection"));
        assertTrue(exception.getMessage().contains("${os.detected.classifier}"));
        assertTrue(exception.getMessage().contains("fixed OS/architecture classifier"));
    }

    @Test
    void runtimeClassifierDependenciesFailWithActionableDiagnostic() {
        EffectiveRawPom pom = effective(parser, """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>io.netty</groupId>
                      <artifactId>netty-transport-native-epoll</artifactId>
                      <version>4.2.12.Final</version>
                      <classifier>${os.detected.classifier}</classifier>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);

        PomInterpolationException exception = assertThrows(
                PomInterpolationException.class,
                () -> manager.applyManagedVersions(pom));

        assertTrue(exception.getMessage().contains("Dynamic classifier selection"));
        assertTrue(exception.getMessage().contains("${os.detected.classifier}"));
        assertTrue(exception.getMessage().contains("fixed OS/architecture classifier"));
    }

    @Test
    void optionalClassifierDependenciesAreSkippedBeforeInterpolation() {
        EffectiveRawPom pom = effective(parser, """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>io.netty</groupId>
                      <artifactId>netty-tcnative-boringssl-static</artifactId>
                      <version>2.0.69.Final</version>
                      <classifier>${tcnative.classifier}</classifier>
                      <optional>true</optional>
                    </dependency>
                    <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                      <version>2.0.17</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        java.util.List<RawPomDependency> dependencies = manager.applyManagedVersions(pom);

        assertEquals(1, dependencies.size());
        assertEquals("org.slf4j", dependencies.getFirst().groupId());
        assertEquals("slf4j-api", dependencies.getFirst().artifactId());
    }

    @Test
    void testScopedClassifierDependenciesAreSkippedBeforeInterpolation() {
        EffectiveRawPom pom = effective(parser, """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>software.amazon.cryptools</groupId>
                      <artifactId>AmazonCorrettoCryptoProvider</artifactId>
                      <version>2.5.0</version>
                      <classifier>${corretto.classifier}</classifier>
                      <scope>test</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                      <version>2.0.17</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        java.util.List<RawPomDependency> dependencies = manager.applyManagedVersions(pom);

        assertEquals(1, dependencies.size());
        assertEquals("org.slf4j", dependencies.getFirst().groupId());
        assertEquals("slf4j-api", dependencies.getFirst().artifactId());
    }

    @Test
    void providedScopedClassifierDependenciesAreSkippedBeforeInterpolation() {
        EffectiveRawPom pom = effective(parser, """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>io.netty</groupId>
                      <artifactId>netty-transport-native-epoll</artifactId>
                      <version>4.2.12.Final</version>
                      <classifier>${os.detected.classifier}</classifier>
                      <scope>provided</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                      <version>2.0.17</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        java.util.List<RawPomDependency> dependencies = manager.applyManagedVersions(pom);

        assertEquals(1, dependencies.size());
        assertEquals("org.slf4j", dependencies.getFirst().groupId());
        assertEquals("slf4j-api", dependencies.getFirst().artifactId());
    }
}
