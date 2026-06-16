package com.zolt.maven;

import static com.zolt.maven.PomDependencyManagerTestSupport.effective;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void classifierDependenciesAreSkippedBeforeInterpolation() {
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

        assertTrue(manager.applyManagedVersions(pom).isEmpty());
    }
}
