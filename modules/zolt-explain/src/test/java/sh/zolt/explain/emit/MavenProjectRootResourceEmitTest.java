package sh.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.maven.MavenStaticProjectInspector;
import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenProjectRootResourceEmitTest {
    @TempDir
    private Path tempDir;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    @Test
    void dropsBareProjectRootResourceRootAndAnnotatesIt() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.createDirectories(tempDir.resolve("src/main/java/com/ex"));
        Files.writeString(tempDir.resolve("src/main/java/com/ex/App.java"), "package com.ex; public class App { }\n");
        Files.writeString(tempDir.resolve("LICENSE"), "license text\n");
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.ex</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <packaging>jar</packaging>
                  <build>
                    <resources>
                      <resource>
                        <directory>src/main/resources</directory>
                        <filtering>false</filtering>
                      </resource>
                      <resource>
                        <directory>./</directory>
                        <targetPath>META-INF/demo/</targetPath>
                        <filtering>false</filtering>
                        <includes>
                          <include>LICENSE</include>
                        </includes>
                      </resource>
                    </resources>
                  </build>
                </project>
                """);

        DraftZoltToml draft = mapper.fromMaven(new MavenStaticProjectInspector().inspect(tempDir));
        ProjectConfig config = draft.config();

        assertEquals(List.of("src/main/resources"), config.build().resourceRoots());
        assertFalse(config.build().resourceRoots().contains("./"), config.build().resourceRoots().toString());
        assertFalse(config.build().resourceRoots().contains("."), config.build().resourceRoots().toString());
        assertTrue(
                draft.notes().stream().anyMatch(note ->
                        note.contains("project-root") && note.contains("resource")),
                draft.notes().toString());
    }

    @Test
    void genuineResourceRootEmitsUnchangedWithNoNote() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.createDirectories(tempDir.resolve("src/main/java/com/ex"));
        Files.writeString(tempDir.resolve("src/main/java/com/ex/App.java"), "package com.ex; public class App { }\n");
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.ex</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <packaging>jar</packaging>
                  <build>
                    <resources>
                      <resource>
                        <directory>src/main/resources</directory>
                        <filtering>false</filtering>
                      </resource>
                    </resources>
                  </build>
                </project>
                """);

        DraftZoltToml draft = mapper.fromMaven(new MavenStaticProjectInspector().inspect(tempDir));

        assertEquals(List.of("src/main/resources"), draft.config().build().resourceRoots());
        assertTrue(
                draft.notes().stream().noneMatch(note -> note.contains("project-root")),
                draft.notes().toString());
    }
}
