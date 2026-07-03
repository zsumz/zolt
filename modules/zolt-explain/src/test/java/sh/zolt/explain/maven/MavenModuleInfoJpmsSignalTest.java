package sh.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.ExplainSignal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenModuleInfoJpmsSignalTest {
    @TempDir
    private Path tempDir;

    private final MavenStaticProjectInspector inspector = new MavenStaticProjectInspector();

    @Test
    void emitsBlockerWhenModuleInfoUnderSourceRootAndJavaBelow9() throws IOException {
        writeModularProject("8");

        List<ExplainSignal> jpms = jpmsSignals();

        assertEquals(1, jpms.size());
        ExplainSignal signal = jpms.getFirst();
        assertEquals(ExplainSignal.Severity.BLOCK, signal.severity());
        assertEquals(ExplainSignal.Category.MIGRATION_BLOCKER, signal.category());
        assertTrue(signal.message().contains("module-info.java"), signal.message());
        assertTrue(signal.message().contains("src/main/java"), signal.message());
        assertTrue(signal.message().contains("`8`"), signal.message());
    }

    @Test
    void emitsBlockerForLegacyOneDotEightJavaVersion() throws IOException {
        writeModularProjectWithConfiguration("1.8");

        assertEquals(1, jpmsSignals().size());
    }

    @Test
    void emitsNoBlockerWhenJavaIsNineOrHigher() throws IOException {
        writeModularProject("9");

        assertEquals(List.of(), jpmsSignals());
    }

    @Test
    void emitsNoBlockerWhenJavaIsElevenAndModuleInfoPresent() throws IOException {
        writeModularProject("11");

        assertEquals(List.of(), jpmsSignals());
    }

    @Test
    void emitsNoBlockerForNonModularProject() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.writeString(tempDir.resolve("src/main/java/Demo.java"), "public class Demo { }\n");
        Files.writeString(tempDir.resolve("pom.xml"), pom("8"));

        assertEquals(List.of(), jpmsSignals());
    }

    private List<ExplainSignal> jpmsSignals() {
        return inspector.inspect(tempDir).signals().stream()
                .filter(signal -> "maven.jpms.module-info-detected".equals(signal.id()))
                .toList();
    }

    private void writeModularProject(String release) throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.writeString(
                tempDir.resolve("src/main/java/module-info.java"),
                "module com.example.demo { }\n");
        Files.writeString(tempDir.resolve("src/main/java/Demo.java"), "public class Demo { }\n");
        Files.writeString(tempDir.resolve("pom.xml"), pom(release));
    }

    private void writeModularProjectWithConfiguration(String source) throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.writeString(
                tempDir.resolve("src/main/java/module-info.java"),
                "module com.example.demo { }\n");
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <packaging>jar</packaging>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <configuration>
                          <source>%s</source>
                          <target>%s</target>
                        </configuration>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """.formatted(source, source));
    }

    private static String pom(String release) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <packaging>jar</packaging>
                  <properties>
                    <maven.compiler.release>%s</maven.compiler.release>
                  </properties>
                </project>
                """.formatted(release);
    }
}
