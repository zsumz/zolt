package sh.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.maven.MavenStaticProjectInspector;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProducesLane;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies {@code --emit-toml} drafts {@code kind = "exec"} steps from exec-shaped Maven plugins. */
final class MavenExecStepEmitTest {
    @TempDir
    private Path tempDir;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    @Test
    void draftsJvmExecToolStepForExecJavaWithExecutableDependencies() throws IOException {
        DraftZoltToml draft = draft("""
                <plugin>
                  <groupId>org.codehaus.mojo</groupId>
                  <artifactId>exec-maven-plugin</artifactId>
                  <executions>
                    <execution>
                      <id>generate-api</id>
                      <phase>generate-sources</phase>
                      <goals><goal>java</goal></goals>
                      <configuration>
                        <mainClass>com.example.tool.Main</mainClass>
                        <arguments><argument>--out</argument><argument>target/generated</argument></arguments>
                        <executableDependencies>
                          <dependency><groupId>com.example</groupId><artifactId>codegen-tool</artifactId></dependency>
                        </executableDependencies>
                      </configuration>
                    </execution>
                  </executions>
                </plugin>
                """);
        GeneratedSourceStep step = execStep(draft);
        assertEquals("jvm", step.exec().tool().runner());
        assertEquals("com.example.tool.Main", step.exec().tool().mainClass());
        assertEquals("com.example:codegen-tool", step.exec().tool().coordinates().getFirst().coordinate());
        assertEquals(ProducesLane.JAVA_SOURCES, step.exec().produces());
        assertEquals(List.of("--out", "target/generated"), step.exec().args());
        assertEquals(List.of(MavenExecStepDrafter.INPUT_PLACEHOLDER), step.inputs());
        assertTrue(draft.notes().stream().anyMatch(note -> note.contains("executableDependencies")), draft.notes()::toString);
    }

    @Test
    void draftsProcessToolStepForExecExecGoal() throws IOException {
        DraftZoltToml draft = draft("""
                <plugin>
                  <groupId>org.codehaus.mojo</groupId>
                  <artifactId>exec-maven-plugin</artifactId>
                  <executions>
                    <execution>
                      <phase>generate-resources</phase>
                      <goals><goal>exec</goal></goals>
                      <configuration>
                        <executable>protoc</executable>
                        <arguments><argument>--java_out=target/gen</argument></arguments>
                      </configuration>
                    </execution>
                  </executions>
                </plugin>
                """);
        GeneratedSourceStep step = execStep(draft);
        assertEquals("process", step.exec().tool().runner());
        assertEquals("protoc", step.exec().tool().binary());
        assertTrue(step.exec().tool().allowUnpinnedTool());
        assertEquals(ProducesLane.RESOURCES, step.exec().produces());
    }

    @Test
    void clampsProjectToolSourceLaneToResourcesWithReviewNote() throws IOException {
        DraftZoltToml draft = draft("""
                <plugin>
                  <groupId>org.codehaus.mojo</groupId>
                  <artifactId>exec-maven-plugin</artifactId>
                  <executions>
                    <execution>
                      <phase>generate-sources</phase>
                      <goals><goal>java</goal></goals>
                      <configuration><mainClass>com.example.Generator</mainClass></configuration>
                    </execution>
                  </executions>
                </plugin>
                """);
        GeneratedSourceStep step = execStep(draft);
        assertEquals("project", step.exec().tool().runner());
        assertEquals(ProducesLane.RESOURCES, step.exec().produces());
        assertTrue(draft.notes().stream().anyMatch(note -> note.contains("tool = \"project\"")
                && note.contains("after compile")), draft.notes()::toString);
        assertTrue(draft.notes().stream().anyMatch(note -> note.contains(MavenExecStepDrafter.INPUT_PLACEHOLDER)),
                draft.notes()::toString);
    }

    @Test
    void rendererInjectsInputOutputTodoUnderExecSections() throws IOException {
        DraftZoltToml draft = draft("""
                <plugin>
                  <groupId>org.codehaus.mojo</groupId>
                  <artifactId>exec-maven-plugin</artifactId>
                  <executions>
                    <execution>
                      <phase>generate-resources</phase>
                      <goals><goal>exec</goal></goals>
                      <configuration><executable>protoc</executable></configuration>
                    </execution>
                  </executions>
                </plugin>
                """);
        String rendered = new DraftZoltTomlRenderer().render(draft, config -> """
                [generated.main.gen]
                kind = "exec"
                tool = "protoc"
                inputs = ["REPLACE_ME"]
                output = "target/generated/resources/gen"
                produces = "resources"
                """);
        assertTrue(rendered.contains("[generated.main.gen]\n# TODO declare inputs/outputs"), rendered);
    }

    private DraftZoltToml draft(String pluginXml) throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <build><plugins>%s</plugins></build>
                </project>
                """.formatted(pluginXml));
        return mapper.fromMaven(new MavenStaticProjectInspector().inspect(tempDir));
    }

    private static GeneratedSourceStep execStep(DraftZoltToml draft) {
        return java.util.stream.Stream
                .concat(draft.config().build().generatedMainSources().stream(),
                        draft.config().build().generatedTestSources().stream())
                .filter(step -> step.kind() == GeneratedSourceKind.EXEC)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no exec step drafted: " + draft.config().build()));
    }
}
