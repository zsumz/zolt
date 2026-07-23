package sh.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies {@link MavenExecConfigParser} extraction of exec-shaped plugin invocations from a POM. */
final class MavenExecPluginConfigInspectionTest {
    @TempDir
    private Path tempDir;

    private final MavenStaticProjectInspector inspector = new MavenStaticProjectInspector();

    @Test
    void extractsExecMavenPluginJavaGoalMainClassArgumentsAndPhase() throws IOException {
        List<MavenExecInvocation> invocations = invocations("""
                <plugin>
                  <groupId>org.codehaus.mojo</groupId>
                  <artifactId>exec-maven-plugin</artifactId>
                  <executions>
                    <execution>
                      <id>generate-model</id>
                      <phase>generate-sources</phase>
                      <goals><goal>java</goal></goals>
                      <configuration>
                        <mainClass>com.example.Generator</mainClass>
                        <arguments>
                          <argument>src/main/spec</argument>
                          <argument>target/generated-sources/model</argument>
                        </arguments>
                      </configuration>
                    </execution>
                  </executions>
                </plugin>
                """, "exec-maven-plugin");
        assertEquals(1, invocations.size());
        MavenExecInvocation invocation = invocations.getFirst();
        assertEquals("java", invocation.goal());
        assertEquals("generate-sources", invocation.phase().orElseThrow());
        assertEquals("com.example.Generator", invocation.mainClass().orElseThrow());
        assertEquals(List.of("src/main/spec", "target/generated-sources/model"), invocation.arguments());
        assertTrue(invocation.mappable());
    }

    @Test
    void extractsExecMavenPluginExecGoalBinaryWorkingDirectoryAndEnvironment() throws IOException {
        List<MavenExecInvocation> invocations = invocations("""
                <plugin>
                  <groupId>org.codehaus.mojo</groupId>
                  <artifactId>exec-maven-plugin</artifactId>
                  <executions>
                    <execution>
                      <phase>generate-resources</phase>
                      <goals><goal>exec</goal></goals>
                      <configuration>
                        <executable>protoc</executable>
                        <workingDirectory>${project.basedir}/proto</workingDirectory>
                        <arguments>
                          <argument>--java_out=target/generated</argument>
                          <argument>service.proto</argument>
                        </arguments>
                        <environmentVariables>
                          <PROTOC_MODE>release</PROTOC_MODE>
                        </environmentVariables>
                      </configuration>
                    </execution>
                  </executions>
                </plugin>
                """, "exec-maven-plugin");
        MavenExecInvocation invocation = invocations.getFirst();
        assertEquals("exec", invocation.goal());
        assertEquals("protoc", invocation.executable().orElseThrow());
        assertEquals("proto", invocation.workingDirectory().orElseThrow());
        assertEquals(List.of("--java_out=target/generated", "service.proto"), invocation.arguments());
        assertEquals("release", invocation.environmentVariables().get("PROTOC_MODE"));
    }

    @Test
    void flagsShellMetacharactersInExecArgumentsAsNotMappable() throws IOException {
        List<MavenExecInvocation> invocations = invocations("""
                <plugin>
                  <groupId>org.codehaus.mojo</groupId>
                  <artifactId>exec-maven-plugin</artifactId>
                  <executions>
                    <execution>
                      <goals><goal>exec</goal></goals>
                      <configuration>
                        <executable>sh</executable>
                        <arguments>
                          <argument>-c</argument>
                          <argument>gen &amp;&amp; build</argument>
                        </arguments>
                      </configuration>
                    </execution>
                  </executions>
                </plugin>
                """, "exec-maven-plugin");
        MavenExecInvocation invocation = invocations.getFirst();
        assertTrue(invocation.shellUnsafe());
        assertFalse(invocation.mappable());
    }

    @Test
    void capturesExecutableDependenciesForJvmToolCoordinates() throws IOException {
        List<MavenExecInvocation> invocations = invocations("""
                <plugin>
                  <groupId>org.codehaus.mojo</groupId>
                  <artifactId>exec-maven-plugin</artifactId>
                  <executions>
                    <execution>
                      <phase>generate-sources</phase>
                      <goals><goal>java</goal></goals>
                      <configuration>
                        <mainClass>com.example.tool.Main</mainClass>
                        <executableDependencies>
                          <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>codegen-tool</artifactId>
                          </dependency>
                        </executableDependencies>
                      </configuration>
                    </execution>
                  </executions>
                </plugin>
                """, "exec-maven-plugin");
        assertEquals(List.of("com.example:codegen-tool"), invocations.getFirst().executableDependencies());
    }

    @Test
    void extractsFrontendMavenPluginPerGoalArgumentsAndWorkingDirectory() throws IOException {
        List<MavenExecInvocation> invocations = invocations("""
                <plugin>
                  <groupId>com.github.eirslett</groupId>
                  <artifactId>frontend-maven-plugin</artifactId>
                  <configuration>
                    <workingDirectory>src/main/frontend</workingDirectory>
                  </configuration>
                  <executions>
                    <execution>
                      <id>npm install</id>
                      <goals><goal>npm</goal></goals>
                      <configuration><arguments>ci</arguments></configuration>
                    </execution>
                    <execution>
                      <id>npm build</id>
                      <phase>generate-resources</phase>
                      <goals><goal>npm</goal></goals>
                      <configuration><arguments>run build</arguments></configuration>
                    </execution>
                  </executions>
                </plugin>
                """, "frontend-maven-plugin");
        assertEquals(2, invocations.size());
        MavenExecInvocation install = invocations.getFirst();
        assertEquals("npm", install.executable().orElseThrow());
        assertEquals(List.of("ci"), install.arguments());
        assertEquals("src/main/frontend", install.workingDirectory().orElseThrow());
        MavenExecInvocation build = invocations.get(1);
        assertEquals(List.of("run", "build"), build.arguments());
        assertEquals("generate-resources", build.phase().orElseThrow());
    }

    @Test
    void mapsAntrunSingleExecTargetButFlagsMultiTaskTargetAsControlFlow() throws IOException {
        List<MavenExecInvocation> single = invocations("""
                <plugin>
                  <artifactId>maven-antrun-plugin</artifactId>
                  <executions>
                    <execution>
                      <phase>generate-resources</phase>
                      <goals><goal>run</goal></goals>
                      <configuration>
                        <target>
                          <exec executable="./codegen.sh">
                            <arg value="--out"/>
                            <arg value="target/generated"/>
                          </exec>
                        </target>
                      </configuration>
                    </execution>
                  </executions>
                </plugin>
                """, "maven-antrun-plugin");
        MavenExecInvocation invocation = single.getFirst();
        assertEquals("./codegen.sh", invocation.executable().orElseThrow());
        assertEquals(List.of("--out", "target/generated"), invocation.arguments());
        assertTrue(invocation.mappable());

        List<MavenExecInvocation> multi = invocations("""
                <plugin>
                  <artifactId>maven-antrun-plugin</artifactId>
                  <executions>
                    <execution>
                      <phase>generate-resources</phase>
                      <goals><goal>run</goal></goals>
                      <configuration>
                        <target>
                          <mkdir dir="target/generated"/>
                          <exec executable="./codegen.sh"/>
                        </target>
                      </configuration>
                    </execution>
                  </executions>
                </plugin>
                """, "maven-antrun-plugin");
        assertTrue(multi.getFirst().controlFlow());
        assertFalse(multi.getFirst().mappable());
    }

    private List<MavenExecInvocation> invocations(String pluginXml, String coordinateSuffix) throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <build><plugins>%s</plugins></build>
                </project>
                """.formatted(pluginXml));
        return inspector.inspect(tempDir).projects().getFirst().plugins().stream()
                .filter(plugin -> plugin.coordinate().contains(coordinateSuffix))
                .findFirst()
                .orElseThrow()
                .execInvocations();
    }
}
