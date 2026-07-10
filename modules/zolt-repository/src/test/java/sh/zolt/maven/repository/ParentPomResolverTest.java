package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.maven.Coordinate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ParentPomResolverTest {
    private final RawPomParser parser = new RawPomParser();

    @Test
    void childPomInheritsGroupIdFromParent() {
        MapBackedSource source = new MapBackedSource();
        source.put("com.example:parent:1.0.0", pom("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                </project>
                """));
        RawPom child = pom("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                </project>
                """);

        EffectiveRawPom effective = new ParentPomResolver(source).resolve(child);

        assertEquals("com.example", effective.groupId());
    }

    @Test
    void childPomInheritsVersionFromParent() {
        MapBackedSource source = new MapBackedSource();
        source.put("com.example:parent:1.0.0", pom("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                </project>
                """));
        RawPom child = pom("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                  <groupId>com.example.app</groupId>
                </project>
                """);

        EffectiveRawPom effective = new ParentPomResolver(source).resolve(child);

        assertEquals("1.0.0", effective.version());
    }

    @Test
    void parentPropertiesAreAvailableForNormalization() {
        MapBackedSource source = new MapBackedSource();
        source.put("com.example:parent:1.0.0", pom("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <junit.version>5.11.4</junit.version>
                    <shared.version>parent</shared.version>
                  </properties>
                </project>
                """));
        RawPom child = pom("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                  <properties>
                    <shared.version>child</shared.version>
                  </properties>
                </project>
                """);

        EffectiveRawPom effective = new ParentPomResolver(source).resolve(child);

        assertEquals("5.11.4", effective.properties().get("junit.version"));
        assertEquals("child", effective.properties().get("shared.version"));
    }

    @Test
    void parentDependencyManagementIsAvailableForNormalization() {
        MapBackedSource source = new MapBackedSource();
        source.put("com.example:parent:1.0.0", pom("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>5.11.4</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """));
        RawPom child = pom("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                </project>
                """);

        EffectiveRawPom effective = new ParentPomResolver(source).resolve(child);

        assertEquals(1, effective.dependencyManagement().size());
        assertEquals("org.junit.jupiter", effective.dependencyManagement().getFirst().groupId());
        assertEquals("junit-jupiter", effective.dependencyManagement().getFirst().artifactId());
    }

    @Test
    void childDependenciesOverrideParentDependenciesByKey() {
        MapBackedSource source = new MapBackedSource();
        source.put("com.example:parent:1.0.0", pom("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>runtime-client</artifactId>
                      <version>2.0.0</version>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
                </project>
                """));
        RawPom child = pom("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>runtime-client</artifactId>
                      <version>1.0.0</version>
                      <scope>runtime</scope>
                      <exclusions>
                        <exclusion>
                          <groupId>com.example</groupId>
                          <artifactId>legacy-helper</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                </project>
                """);

        EffectiveRawPom effective = new ParentPomResolver(source).resolve(child);

        assertEquals(1, effective.dependencies().size());
        RawPomDependency dependency = effective.dependencies().getFirst();
        assertEquals("runtime-client", dependency.artifactId());
        assertEquals("1.0.0", dependency.version().orElseThrow());
        assertEquals(List.of(new RawPomExclusion("com.example", "legacy-helper")), dependency.exclusions());
    }

    @Test
    void loadsMultiLevelParentChainRootFirst() {
        MapBackedSource source = new MapBackedSource();
        source.put("com.example:parent:1.0.0", pom("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>grandparent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>parent</artifactId>
                </project>
                """));
        source.put("com.example:grandparent:1.0.0", pom("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>grandparent</artifactId>
                  <version>1.0.0</version>
                </project>
                """));
        RawPom child = pom("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                </project>
                """);

        EffectiveRawPom effective = new ParentPomResolver(source).resolve(child);

        assertEquals(List.of("grandparent", "parent"), effective.parents().stream().map(RawPom::artifactId).toList());
        assertEquals("com.example", effective.groupId());
        assertEquals("1.0.0", effective.version());
    }

    @Test
    void detectsParentPomCycle() {
        MapBackedSource source = new MapBackedSource();
        source.put("com.example:a:1.0.0", pom("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>b</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>a</artifactId>
                </project>
                """));
        source.put("com.example:b:1.0.0", pom("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>a</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>b</artifactId>
                </project>
                """));
        RawPom child = pom("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>a</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                </project>
                """);

        ParentPomException exception = assertThrows(
                ParentPomException.class,
                () -> new ParentPomResolver(source).resolve(child));

        assertTrue(exception.getMessage().contains("Parent POM cycle detected"));
        assertTrue(exception.getMessage().contains("com.example:app:1.0.0 -> com.example:a:1.0.0"));
        assertTrue(exception.getMessage().contains("Remove the circular <parent> reference"));
    }

    @Test
    void detectsSelfParentWithoutLoadingParentPom() {
        RawPom child = pom("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                </project>
                """);

        ParentPomException exception = assertThrows(
                ParentPomException.class,
                () -> new ParentPomResolver(coordinate -> {
                    throw new AssertionError("self-parent should be detected before loading " + coordinate);
                }).resolve(child));

        assertEquals(
                "Parent POM cycle detected: com.example:app:1.0.0 -> com.example:app:1.0.0. Remove the circular <parent> reference from one of these POMs.",
                exception.getMessage());
    }

    @Test
    void missingInheritedGroupIdIsActionable() {
        ParentPomException exception = assertThrows(
                ParentPomException.class,
                () -> new ParentPomResolver(coordinate -> {
                    throw new AssertionError("no parent should be loaded");
                }).resolve(pom("""
                        <project>
                          <artifactId>app</artifactId>
                          <version>1.0.0</version>
                        </project>
                        """)));

        assertEquals("POM app does not declare or inherit a groupId.", exception.getMessage());
    }

    @Test
    void missingInheritedVersionIsActionable() {
        ParentPomException exception = assertThrows(
                ParentPomException.class,
                () -> new ParentPomResolver(coordinate -> {
                    throw new AssertionError("no parent should be loaded");
                }).resolve(pom("""
                        <project>
                          <groupId>com.example</groupId>
                          <artifactId>app</artifactId>
                        </project>
                        """)));

        assertEquals("POM app does not declare or inherit a version.", exception.getMessage());
    }

    private RawPom pom(String xml) {
        return parser.parse(xml);
    }

    private static final class MapBackedSource implements ParentPomSource {
        private final Map<String, RawPom> poms = new HashMap<>();

        void put(String coordinate, RawPom pom) {
            poms.put(coordinate, pom);
        }

        @Override
        public RawPom load(Coordinate coordinate) {
            RawPom pom = poms.get(coordinate.toString());
            if (pom == null) {
                throw new ParentPomException("No test parent POM for " + coordinate);
            }
            return pom;
        }
    }
}
