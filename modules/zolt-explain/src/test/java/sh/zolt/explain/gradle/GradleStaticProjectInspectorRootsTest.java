package sh.zolt.explain.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GradleStaticProjectInspectorRootsTest {
    @TempDir
    private Path tempDir;

    private final GradleStaticProjectInspector inspector = new GradleStaticProjectInspector();

    @Test
    void omitsConventionSourceRootsThatDoNotExistOnDisk() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'bare'\n");
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }\n");

        GradleProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertEquals(List.of(), project.sourceRoots());
        assertEquals(List.of(), project.testSourceRoots());
    }

    @Test
    void keepsExplicitSourceSetRootsEvenWhenAbsent() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'explicit-roots'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                sourceSets {
                    main {
                        java {
                            srcDirs = ['src/java']
                        }
                    }
                    test {
                        java {
                            srcDirs += ['src/tests']
                        }
                    }
                }
                """);

        GradleProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertEquals(List.of("src/java"), project.sourceRoots());
        assertEquals(List.of("src/tests"), project.testSourceRoots());
    }
}
