package com.zolt.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
