package sh.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.maven.MavenStaticProjectInspector;
import sh.zolt.project.BomSettings;
import sh.zolt.project.PackageMode;
import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenBomEmitTest {
    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    @Test
    void draftsBomMemberFromStandaloneDependencyManagementPom(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme.platform</groupId>
                  <artifactId>acme-bom</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.postgresql</groupId>
                        <artifactId>postgresql</artifactId>
                        <version>42.7.4</version>
                      </dependency>
                      <dependency>
                        <groupId>com.fasterxml.jackson</groupId>
                        <artifactId>jackson-bom</artifactId>
                        <version>2.17.0</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);

        DraftZoltToml draft = mapper.fromMaven(new MavenStaticProjectInspector().inspect(tempDir));
        ProjectConfig config = draft.config();

        assertEquals(PackageMode.BOM, config.packageSettings().mode());
        BomSettings bom = config.packageSettings().bom();
        // The plain pin becomes a [bom.versions] entry.
        assertTrue(bom.versions().stream().anyMatch(
                version -> version.coordinate().equals("org.postgresql:postgresql")
                        && version.version().equals("42.7.4")));
        // The import-scope BOM becomes a [bom.imports] entry.
        assertTrue(bom.imports().stream().anyMatch(
                imported -> imported.coordinate().equals("com.fasterxml.jackson:jackson-bom")
                        && imported.version().equals("2.17.0")));
        // A BOM declares no dependencies.
        assertTrue(config.dependencies().isEmpty());
    }
}
