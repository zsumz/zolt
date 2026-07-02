package sh.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.MigrationReadinessConcern;
import sh.zolt.explain.MigrationReadinessScorecard;
import sh.zolt.explain.MigrationReadinessScorecardFormatter;
import sh.zolt.explain.MigrationReadinessScorecards;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenUnknownSignalInspectionTest {
    @TempDir
    private Path tempDir;

    private final MavenStaticProjectInspector inspector = new MavenStaticProjectInspector();

    @Test
    void unresolvedExternalParentFactsBecomeUnknownSignalsAndScorecardConcerns() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>org.apache.commons</groupId>
                    <artifactId>commons-parent</artifactId>
                    <version>102</version>
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>legacy</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-inline</artifactId>
                      <version>${commons.mockito.version}</version>
                    </dependency>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        Set<String> signalIds = result.signals().stream()
                .map(sh.zolt.explain.ExplainSignal::id)
                .collect(java.util.stream.Collectors.toSet());
        String text = new MavenExplainFormatter().text(result);
        String json = new MavenExplainFormatter().json(result);
        MigrationReadinessScorecard scorecard = MigrationReadinessScorecards.from(result);
        String scorecardText = new MigrationReadinessScorecardFormatter().text(scorecard);

        assertTrue(signalIds.contains("maven.dependency.unresolved-version"), () -> result.signals().toString());
        assertTrue(signalIds.contains("maven.dependency.missing-version"), () -> result.signals().toString());
        assertTrue(signalIds.contains("maven.parent.unresolved"), () -> result.signals().toString());
        assertTrue(signalIds.contains("maven.java-version.unknown"), () -> result.signals().toString());
        assertTrue(text.contains("Signals: 4"), () -> text);
        assertTrue(text.contains("unknown  Dependency `org.mockito:mockito-inline:${commons.mockito.version}`"),
                () -> text);
        assertTrue(json.contains("\"signals\": 4"), () -> json);
        assertTrue(json.contains("\"unknown\": 4"), () -> json);
        assertTrue(json.contains("\"status\": \"manual-review\""), () -> json);
        assertEquals("unknown", scorecard.status());
        assertEquals("unknown", concern(scorecard, "dependencies").status());
        assertEquals("unknown", concern(scorecard, "repositories").status());
        assertEquals("unknown", concern(scorecard, "ci").status());
        assertTrue(scorecardText.contains("unresolved Maven dependency version property"), () -> scorecardText);
        assertTrue(scorecardText.contains("unknown Maven Java version"), () -> scorecardText);
    }

    @Test
    void resolvableMavenProjectStaysSignalFree() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>service</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                    <mockito.version>5.12.0</mockito.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-core</artifactId>
                      <version>${mockito.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        String json = new MavenExplainFormatter().json(result);

        assertTrue(result.signals().isEmpty(), () -> result.signals().toString());
        assertTrue(json.contains("\"status\": \"ready\""), () -> json);
    }

    private static MigrationReadinessConcern concern(MigrationReadinessScorecard scorecard, String name) {
        return scorecard.concerns().stream()
                .filter(concern -> concern.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
