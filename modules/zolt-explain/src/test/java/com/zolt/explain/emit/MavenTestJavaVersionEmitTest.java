package com.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.explain.maven.MavenInspectionResult;
import com.zolt.explain.maven.MavenStaticProjectInspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenTestJavaVersionEmitTest {
    @TempDir
    private Path tempDir;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    @Test
    void divergentMavenTestJavaVersionBecomesReviewItem() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>test-level</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.release>8</maven.compiler.release>
                    <maven.compiler.testRelease>17</maven.compiler.testRelease>
                  </properties>
                </project>
                """);

        MavenInspectionResult result = new MavenStaticProjectInspector().inspect(tempDir);
        DraftZoltToml draft = mapper.fromMaven(result);

        assertEquals("8", draft.config().project().java());
        assertTrue(draft.notes().stream()
                .anyMatch(note -> note.contains("test Java version `17` differs from main Java version `8`")),
                () -> draft.notes().toString());
    }
}
