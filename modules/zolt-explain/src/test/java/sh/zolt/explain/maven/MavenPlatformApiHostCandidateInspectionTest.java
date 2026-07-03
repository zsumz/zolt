package sh.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenPlatformApiHostCandidateInspectionTest {
    @TempDir
    private Path tempDir;

    private final MavenStaticProjectInspector inspector = new MavenStaticProjectInspector();

    @Test
    void sourceTargetBelowBuildJdkFiresHostCandidateSignal() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>legacy</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.source>8</maven.compiler.source>
                    <maven.compiler.target>8</maven.compiler.target>
                  </properties>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);

        assertTrue(signalIds(result).contains("maven.compiler.platform-api-host-candidate"),
                () -> result.signals().toString());
    }

    @Test
    void releasePomDoesNotFireHostCandidateSignal() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>strict</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.release>8</maven.compiler.release>
                  </properties>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);

        assertFalse(signalIds(result).contains("maven.compiler.platform-api-host-candidate"),
                () -> result.signals().toString());
        assertTrue(result.projects().get(0).javaVersionProvenance() == MavenJavaVersionProvenance.RELEASE);
    }

    @Test
    void sourceTargetProvenanceIsRecorded() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>legacy</artifactId>
                  <version>1.0.0</version>
                  <build>
                    <plugins>
                      <plugin>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <configuration>
                          <source>8</source>
                          <target>8</target>
                        </configuration>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);

        assertTrue(result.projects().get(0).javaVersionProvenance() == MavenJavaVersionProvenance.SOURCE_TARGET);
        assertTrue(signalIds(result).contains("maven.compiler.platform-api-host-candidate"),
                () -> result.signals().toString());
    }

    private static Set<String> signalIds(MavenInspectionResult result) {
        return result.signals().stream()
                .map(sh.zolt.explain.ExplainSignal::id)
                .collect(Collectors.toSet());
    }
}
