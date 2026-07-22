package sh.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class MavenExternalParentRecoveryInspectionTest extends MavenExternalParentRecoveryTestSupport {
    private static final String SERVICE_WITH_GUAVA = """
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
              <dependencies>
                <dependency>
                  <groupId>com.google.guava</groupId>
                  <artifactId>guava</artifactId>
                </dependency>
              </dependencies>
            </project>
            """;

    @Test
    void externalSingleParentSuppliesDependencyVersion() throws IOException {
        putPom("com.acme.platform", "acme-parent", "1.2.3", """
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
                """);
        Path project = writeProject(SERVICE_WITH_GUAVA);

        MavenInspectionResult result = recoveringInspector().inspect(project);
        MavenProjectInspection service = result.projects().getFirst();
        MavenDependencyInspection guava = dependency(service, "com.google.guava:guava");
        Set<String> signalIds = signalIds(result);

        assertEquals("33.4.8-jre", guava.version(), "external parent supplies the managed version");
        assertEquals("com.google.guava:guava:33.4.8-jre", guava.coordinate());
        assertTrue(service.parents().getFirst().resolved(), "recovered external parent counts as resolved");
        assertFalse(service.parents().getFirst().inReactor());
        assertFalse(signalIds.contains("maven.parent.unresolved"), () -> signalIds.toString());
        assertFalse(signalIds.contains("maven.dependency.missing-version"), () -> signalIds.toString());
        assertTrue(signalIds.contains("maven.external-parent.resolved"), () -> signalIds.toString());
        assertTrue(new MavenExplainFormatter().text(result).contains("external/recovered"),
                () -> new MavenExplainFormatter().text(result));
    }

    @Test
    void offlineKeepsExternalParentUnresolvedAndMissingVersion() throws IOException {
        putPom("com.acme.platform", "acme-parent", "1.2.3", "<project/>");
        Path project = writeProject(SERVICE_WITH_GUAVA);

        MavenInspectionResult result = new MavenStaticProjectInspector().inspect(project);
        MavenDependencyInspection guava = dependency(result.projects().getFirst(), "com.google.guava:guava");
        Set<String> signalIds = signalIds(result);

        assertEquals("", guava.version(), "offline audit never fetches the external parent");
        assertFalse(result.projects().getFirst().parents().getFirst().resolved());
        assertTrue(signalIds.contains("maven.parent.unresolved"), () -> signalIds.toString());
        assertTrue(signalIds.contains("maven.dependency.missing-version"), () -> signalIds.toString());
        assertFalse(signalIds.contains("maven.external-parent.resolved"), () -> signalIds.toString());
        assertTrue(new MavenExplainFormatter().text(result).contains("external/unresolved"),
                () -> new MavenExplainFormatter().text(result));
    }

    @Test
    void multiLevelExternalChainInterpolatesPropertiesAcrossIt() throws IOException {
        putPom("com.acme.platform", "acme-parent", "2.0.0", """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.acme.platform</groupId>
                    <artifactId>acme-super</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <groupId>com.acme.platform</groupId>
                  <artifactId>acme-parent</artifactId>
                  <version>2.0.0</version>
                  <packaging>pom</packaging>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.google.guava</groupId>
                        <artifactId>guava</artifactId>
                        <version>${guava.version}</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        putPom("com.acme.platform", "acme-super", "1.0.0", """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme.platform</groupId>
                  <artifactId>acme-super</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <properties>
                    <guava.version>33.4.8-jre</guava.version>
                  </properties>
                </project>
                """);
        Path project = writeProject("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.acme.platform</groupId>
                    <artifactId>acme-parent</artifactId>
                    <version>2.0.0</version>
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>service</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = recoveringInspector().inspect(project);
        MavenDependencyInspection guava = dependency(result.projects().getFirst(), "com.google.guava:guava");

        assertEquals("33.4.8-jre", guava.version(),
                "a property declared by the grandparent resolves the parent's managed version");
        assertTrue(signalIds(result).contains("maven.external-parent.resolved"));
    }

    @Test
    void importedBomAndNestedBomRecovered() throws IOException {
        putPom("com.acme.platform", "acme-parent", "1.0.0", """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme.platform</groupId>
                  <artifactId>acme-parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.acme.platform</groupId>
                        <artifactId>acme-bom</artifactId>
                        <version>1.0.0</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        putPom("com.acme.platform", "acme-bom", "1.0.0", """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme.platform</groupId>
                  <artifactId>acme-bom</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.acme.platform</groupId>
                        <artifactId>nested-bom</artifactId>
                        <version>1.0.0</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                      <dependency>
                        <groupId>com.acme</groupId>
                        <artifactId>lib-a</artifactId>
                        <version>1.1.1</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        putPom("com.acme.platform", "nested-bom", "1.0.0", """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme.platform</groupId>
                  <artifactId>nested-bom</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.acme</groupId>
                        <artifactId>lib-b</artifactId>
                        <version>2.2.2</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path project = writeProject("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.acme.platform</groupId>
                    <artifactId>acme-parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>service</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.acme</groupId>
                      <artifactId>lib-a</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>com.acme</groupId>
                      <artifactId>lib-b</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenProjectInspection service = recoveringInspector().inspect(project).projects().getFirst();

        assertEquals("1.1.1", dependency(service, "com.acme:lib-a").version(), "imported BOM supplies lib-a");
        assertEquals("2.2.2", dependency(service, "com.acme:lib-b").version(), "nested imported BOM supplies lib-b");
    }

    @Test
    void dynamicVersionInRecoveredManagementBecomesReviewItem() throws IOException {
        putPom("com.acme.platform", "acme-parent", "1.2.3", """
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
                        <version>[30.0,)</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path project = writeProject(SERVICE_WITH_GUAVA);

        MavenInspectionResult result = recoveringInspector().inspect(project);
        MavenDependencyInspection guava = dependency(result.projects().getFirst(), "com.google.guava:guava");

        assertEquals("[30.0,)", guava.version(), "the dynamic managed version is surfaced verbatim, not guessed");
        assertTrue(signalIds(result).contains("maven.dependency.dynamic-version"), () -> signalIds(result).toString());
    }

    @Test
    void snapshotExternalParentIsNotFetched() throws IOException {
        putPom("com.acme.platform", "acme-parent", "9.9.9-SNAPSHOT", """
                <project><modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme.platform</groupId><artifactId>acme-parent</artifactId>
                  <version>9.9.9-SNAPSHOT</version><packaging>pom</packaging></project>
                """);
        Path project = writeProject(SERVICE_WITH_GUAVA.replace("1.2.3", "9.9.9-SNAPSHOT"));

        MavenInspectionResult result = recoveringInspector().inspect(project);
        Set<String> signalIds = signalIds(result);

        assertEquals("", dependency(result.projects().getFirst(), "com.google.guava:guava").version(),
                "remote SNAPSHOT parents are a documented non-goal and stay unfetched");
        assertFalse(result.projects().getFirst().parents().getFirst().resolved());
        assertTrue(signalIds.contains("maven.parent.unresolved"), () -> signalIds.toString());
        assertFalse(signalIds.contains("maven.external-parent.resolved"), () -> signalIds.toString());
        assertTrue(result.signals().stream()
                .filter(signal -> signal.id().equals("maven.parent.unresolved"))
                .anyMatch(signal -> signal.nextStep().contains("SNAPSHOT")),
                () -> result.signals().toString());
    }

    @Test
    void recoveredManagedExclusionsApplyToDependency() throws IOException {
        putPom("com.acme.platform", "acme-parent", "1.2.3", """
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
                        <exclusions>
                          <exclusion>
                            <groupId>com.google.guava</groupId>
                            <artifactId>failureaccess</artifactId>
                          </exclusion>
                        </exclusions>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path project = writeProject(SERVICE_WITH_GUAVA);

        MavenDependencyInspection guava =
                dependency(recoveringInspector().inspect(project).projects().getFirst(), "com.google.guava:guava");

        assertEquals("33.4.8-jre", guava.version());
        assertTrue(guava.exclusions().stream().anyMatch(exclusion ->
                        exclusion.groupId().equals("com.google.guava")
                                && exclusion.artifactId().equals("failureaccess")),
                () -> "managed-entry exclusions must be honored: " + guava.exclusions());
    }

    @Test
    void unreachableExternalParentDegradesGracefully() throws IOException {
        // acme-parent is never served: every repository returns 404. Recovery must not crash.
        Path project = writeProject(SERVICE_WITH_GUAVA);

        MavenInspectionResult result = recoveringInspector().inspect(project);
        Set<String> signalIds = signalIds(result);

        assertEquals("", dependency(result.projects().getFirst(), "com.google.guava:guava").version());
        assertFalse(result.projects().getFirst().parents().getFirst().resolved());
        assertTrue(signalIds.contains("maven.parent.unresolved"), () -> signalIds.toString());
        assertFalse(signalIds.contains("maven.external-parent.resolved"), () -> signalIds.toString());
        assertTrue(result.signals().stream()
                .filter(signal -> signal.id().equals("maven.parent.unresolved"))
                .anyMatch(signal -> signal.nextStep().contains("Could not recover external parent")),
                () -> result.signals().toString());
    }

    private static MavenDependencyInspection dependency(MavenProjectInspection project, String groupArtifact) {
        return project.dependencies().stream()
                .filter(dependency -> {
                    String[] parts = dependency.coordinate().split(":");
                    return parts.length >= 2 && (parts[0] + ":" + parts[1]).equals(groupArtifact);
                })
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("no dependency " + groupArtifact + " in " + project.dependencies()));
    }

    private static Set<String> signalIds(MavenInspectionResult result) {
        return result.signals().stream().map(sh.zolt.explain.ExplainSignal::id).collect(Collectors.toSet());
    }
}
