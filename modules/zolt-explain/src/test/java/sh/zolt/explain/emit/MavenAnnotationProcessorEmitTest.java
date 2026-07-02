package sh.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.maven.MavenStaticProjectInspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenAnnotationProcessorEmitTest {
    @TempDir
    private Path tempDir;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    @Test
    void mavenDraftEmitsAnnotationProcessorPaths() throws IOException {
        writePomWithProcessorVersion("${mapstruct.version}");

        DraftZoltToml draft = mapper.fromMaven(new MavenStaticProjectInspector().inspect(tempDir));

        assertEquals("1.6.3", draft.config().annotationProcessors().get("org.mapstruct:mapstruct-processor"),
                () -> "[annotationProcessors] must carry maven-compiler-plugin processor paths: "
                        + draft.config().annotationProcessors());
        assertFalse(draft.notes().stream().anyMatch(note -> note.contains("mapstruct-processor")),
                () -> "resolved processors should not need review notes: " + draft.notes());
    }

    @Test
    void mavenDraftNotesUnresolvableAnnotationProcessorVersion() throws IOException {
        writePomWithProcessorVersion("${missing.processor.version}");

        DraftZoltToml draft = mapper.fromMaven(new MavenStaticProjectInspector().inspect(tempDir));

        assertFalse(draft.config().annotationProcessors().containsKey("org.mapstruct:mapstruct-processor"),
                () -> "unresolved processors must not be emitted as real versions: "
                        + draft.config().annotationProcessors());
        assertTrue(draft.notes().stream().anyMatch(note ->
                        note.contains("Annotation processor `org.mapstruct:mapstruct-processor`")
                                && note.contains("property")
                                && note.contains("could not resolve")),
                () -> "expected an honest review note for the unresolved processor: " + draft.notes());
    }

    private void writePomWithProcessorVersion(String version) throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <mapstruct.version>1.6.3</mapstruct.version>
                  </properties>
                  <build>
                    <plugins>
                      <plugin>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <configuration>
                          <annotationProcessorPaths>
                            <path>
                              <groupId>org.mapstruct</groupId>
                              <artifactId>mapstruct-processor</artifactId>
                              <version>%s</version>
                            </path>
                          </annotationProcessorPaths>
                        </configuration>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """.formatted(version));
    }
}
