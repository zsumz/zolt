package sh.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.maven.MavenExternalParentRecoveryTestSupport;
import sh.zolt.explain.maven.MavenStaticProjectInspector;
import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MavenExternalParentRecoveryEmitTest extends MavenExternalParentRecoveryTestSupport {
    private static final String ACME_PARENT = """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme.platform</groupId>
              <artifactId>acme-parent</artifactId>
              <version>1.2.3</version>
              <packaging>pom</packaging>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>com.google.guava</groupId>
                    <artifactId>guava</artifactId>
                    <version>33.4.8-jre</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
            </project>
            """;
    private static final String SERVICE = """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <parent>
                <groupId>com.acme.platform</groupId>
                <artifactId>acme-parent</artifactId>
                <version>1.2.3</version>
              </parent>
              <groupId>com.example</groupId>
              <artifactId>service</artifactId>
              <version>1.0.0</version>
              <properties>
                <maven.compiler.release>21</maven.compiler.release>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>com.google.guava</groupId>
                  <artifactId>guava</artifactId>
                </dependency>
              </dependencies>
            </project>
            """;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    @Test
    void recoveredExternalParentInlinesFixedVersionAndIsDeterministic() throws IOException {
        putPom("com.acme.platform", "acme-parent", "1.2.3", ACME_PARENT);
        Path project = writeProject(SERVICE);

        DraftZoltToml first = mapper.fromMaven(recoveringInspector().inspect(project));
        DraftZoltToml second = mapper.fromMaven(recoveringInspector().inspect(project));
        ProjectConfig config = first.config();

        assertEquals("33.4.8-jre", config.dependencies().get("com.google.guava:guava"),
                () -> "recovered managed version becomes a fixed literal in the draft: " + config.dependencies());
        assertFalse(first.notes().stream().anyMatch(note ->
                        note.contains("com.google.guava:guava") && note.contains("no static version")),
                () -> "no missing-version review note should remain: " + first.notes());
        assertEquals(config.dependencies(), second.config().dependencies(),
                "the emitted draft is deterministic across re-runs (cache-backed)");
    }

    @Test
    void offlineDraftKeepsGuavaVersionlessWithReviewNote() throws IOException {
        Path project = writeProject(SERVICE);

        DraftZoltToml draft = mapper.fromMaven(new MavenStaticProjectInspector().inspect(project));

        assertFalse(draft.config().dependencies().containsKey("com.google.guava:guava"),
                () -> "offline audit cannot fetch the parent, so guava has no version: "
                        + draft.config().dependencies());
        assertTrue(draft.notes().stream().anyMatch(note ->
                        note.contains("com.google.guava:guava") && note.contains("no static version")),
                () -> "offline draft keeps the honest missing-version review note: " + draft.notes());
    }
}
