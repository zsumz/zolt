package sh.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.maven.MavenInspectionResult;
import sh.zolt.explain.maven.MavenStaticProjectInspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenSnapshotVersionEmitTest {
    @TempDir
    private Path tempDir;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    @Test
    void snapshotProjectVersionStaysLiveAndAddsReviewNote() throws IOException {
        writeProject("1.23.1-SNAPSHOT");

        DraftZoltToml draft = mapper.fromMaven(inspect());

        assertEquals("1.23.1-SNAPSHOT", draft.config().project().version());
        assertTrue(draft.commentedProjectKeys().isEmpty(), draft.commentedProjectKeys().toString());
        assertTrue(
                draft.notes().stream().anyMatch(note ->
                        note.contains("1.23.1-SNAPSHOT")
                                && note.contains("SNAPSHOT")
                                && note.contains("non-determinism")),
                draft.notes().toString());
    }

    @Test
    void releaseProjectVersionEmitsNoSnapshotNote() throws IOException {
        writeProject("1.23.1");

        DraftZoltToml draft = mapper.fromMaven(inspect());

        assertEquals("1.23.1", draft.config().project().version());
        assertTrue(
                draft.notes().stream().noneMatch(note -> note.contains("SNAPSHOT")),
                draft.notes().toString());
    }

    @Test
    void blankVersionKeepsExistingPlaceholderNote() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/java/app"));
        Files.writeString(tempDir.resolve("src/main/java/app/App.java"), "package app; public class App { }\n");
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>demo</artifactId>
                  <packaging>jar</packaging>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                  </properties>
                </project>
                """);

        DraftZoltToml draft = mapper.fromMaven(inspect());

        assertTrue(
                draft.notes().stream().anyMatch(note -> note.contains("`version` is a placeholder")),
                draft.notes().toString());
        assertTrue(
                draft.notes().stream().noneMatch(note -> note.contains("SNAPSHOT")),
                draft.notes().toString());
    }

    private MavenInspectionResult inspect() {
        return new MavenStaticProjectInspector().inspect(tempDir);
    }

    private void writeProject(String version) throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/java/app"));
        Files.writeString(tempDir.resolve("src/main/java/app/App.java"), "package app; public class App { }\n");
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>%s</version>
                  <packaging>jar</packaging>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                  </properties>
                </project>
                """.formatted(version));
    }
}
