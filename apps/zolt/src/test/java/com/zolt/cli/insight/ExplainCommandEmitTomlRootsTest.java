package com.zolt.cli.insight;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExplainCommandEmitTomlRootsTest {
    @TempDir
    private Path tempDir;

    @Test
    void emittedMavenDraftCarriesAuditedSourceTestAndResourceRoots() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>custom-roots</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                  </properties>
                  <build>
                    <sourceDirectory>src/java</sourceDirectory>
                    <testSourceDirectory>src/tests</testSourceDirectory>
                    <resources>
                      <resource>
                        <directory>config</directory>
                      </resource>
                    </resources>
                    <testResources>
                      <testResource>
                        <directory>test-config</directory>
                      </testResource>
                    </testResources>
                    <plugins>
                      <plugin>
                        <groupId>org.codehaus.mojo</groupId>
                        <artifactId>build-helper-maven-plugin</artifactId>
                        <executions>
                          <execution>
                            <goals>
                              <goal>add-source</goal>
                            </goals>
                            <configuration>
                              <sources>
                                <source>src/gen</source>
                              </sources>
                            </configuration>
                          </execution>
                        </executions>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);

        CommandResult result = execute("explain", "--emit-toml", "--cwd", tempDir.toString(), "--source", "maven");

        assertEquals(0, result.exitCode(), () -> result.stderr());
        assertTrue(result.stdout().contains("source = \"src/java\""), () -> result.stdout());
        assertTrue(result.stdout().contains("sources = [\"src/java\", \"src/gen\"]"), () -> result.stdout());
        assertTrue(result.stdout().contains("test = \"src/tests\""), () -> result.stdout());
        assertTrue(result.stdout().contains("[resources]"), () -> result.stdout());
        ProjectConfig parsed = new ZoltTomlParser().parse(result.stdout());
        assertEquals("src/java", parsed.build().source());
        assertEquals(List.of("src/java", "src/gen"), parsed.build().sourceRoots());
        assertEquals("src/tests", parsed.build().test());
        assertEquals(List.of("config"), parsed.build().resourceRoots());
        assertEquals(List.of("test-config"), parsed.build().testResourceRoots());
    }
}
