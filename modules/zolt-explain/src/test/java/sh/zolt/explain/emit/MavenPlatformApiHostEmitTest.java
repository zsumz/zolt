package sh.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.maven.MavenInspectionResult;
import sh.zolt.explain.maven.MavenStaticProjectInspector;
import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenPlatformApiHostEmitTest {
    @TempDir
    private Path tempDir;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();
    private final DraftZoltTomlRenderer renderer = new DraftZoltTomlRenderer();

    @Test
    void sourceTargetBelowJdkEmitsStrictReleaseWithCommentedHostSuggestion() throws IOException {
        DraftZoltToml draft = draftFor("""
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

        assertTrue(draft.suggestCompilerPlatformApiHost());
        assertTrue(draft.notes().stream().anyMatch(note ->
                note.contains("source/target 8 below the build JDK")
                        && note.contains("forfeits cross-JDK reproducibility")),
                () -> draft.notes().toString());

        String rendered = renderer.render(draft, new FakeProjectRenderer());
        assertTrue(rendered.contains("[compiler]"), rendered);
        assertTrue(rendered.contains("# platformApi = \"host\""), rendered);
        // The live value stays strict: no uncommented platformApi assignment.
        assertFalse(rendered.contains("\nplatformApi = \"host\""), rendered);
    }

    @Test
    void releasePomEmitsNoHostSuggestion() throws IOException {
        DraftZoltToml draft = draftFor("""
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

        assertFalse(draft.suggestCompilerPlatformApiHost());
        assertFalse(draft.notes().stream().anyMatch(note -> note.contains("platformApi")),
                () -> draft.notes().toString());

        String rendered = renderer.render(draft, new FakeProjectRenderer());
        assertFalse(rendered.contains("platformApi"), rendered);
    }

    @Test
    void renderedHostSuggestionIsDeterministic() throws IOException {
        DraftZoltToml draft = draftFor("""
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

        String first = renderer.render(draft, new FakeProjectRenderer());
        String second = renderer.render(draft, new FakeProjectRenderer());
        assertTrue(first.equals(second), first);
    }

    private DraftZoltToml draftFor(String pom) throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), pom);
        MavenInspectionResult result = new MavenStaticProjectInspector().inspect(tempDir);
        return mapper.fromMaven(result);
    }

    private static final class FakeProjectRenderer implements ProjectConfigRenderer {
        @Override
        public String render(ProjectConfig config) {
            return """
                    [project]
                    name = "%s"
                    version = "%s"
                    group = "%s"
                    java = "%s"
                    """.formatted(
                    config.project().name(),
                    config.project().version(),
                    config.project().group(),
                    config.project().java());
        }
    }
}
