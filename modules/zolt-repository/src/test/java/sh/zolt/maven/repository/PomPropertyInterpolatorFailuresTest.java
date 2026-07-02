package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PomPropertyInterpolatorFailuresTest {
    private final RawPomParser parser = new RawPomParser();
    private final PomPropertyInterpolator interpolator = new PomPropertyInterpolator();

    @Test
    void unresolvedDependencyPropertyProducesActionableErrorWithCoordinateContext() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.2.3</version>
                </project>
                """);
        RawPomDependency dependency = new RawPomDependency(
                "com.example",
                "library",
                Optional.of("${missing.version}"),
                Optional.of("compile"),
                Optional.empty(),
                Optional.empty(),
                false,
                java.util.List.of());

        PomInterpolationException exception = assertThrows(
                PomInterpolationException.class,
                () -> interpolator.interpolateDependency(dependency, pom));

        assertEquals(
                "Unresolved POM property `${missing.version}` while processing com.example:app:1.2.3. Define the property or declare the value explicitly.",
                exception.getMessage());
    }

    @Test
    void unresolvedPropertyProducesActionableErrorWithCoordinateContext() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.2.3</version>
                </project>
                """);

        PomInterpolationException exception = assertThrows(
                PomInterpolationException.class,
                () -> interpolator.interpolate("${missing.version}", pom));

        assertEquals(
                "Unresolved POM property `${missing.version}` while processing com.example:app:1.2.3. Define the property or declare the value explicitly.",
                exception.getMessage());
    }

    @Test
    void cyclicPropertiesFailClearly() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.2.3</version>
                  <properties>
                    <first.version>${second.version}</first.version>
                    <second.version>${first.version}</second.version>
                  </properties>
                </project>
                """);

        PomInterpolationException exception = assertThrows(
                PomInterpolationException.class,
                () -> interpolator.interpolate("${first.version}", pom));

        assertEquals(
                "Cyclic POM property reference while processing com.example:app:1.2.3: first.version -> second.version -> first.version.",
                exception.getMessage());
    }

    @Test
    void interpolatorLeavesUnknownPropertyTextReadable() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.2.3</version>
                </project>
                """);

        PomInterpolationException exception = assertThrows(
                PomInterpolationException.class,
                () -> interpolator.interpolate("${unknown}", pom));

        assertTrue(exception.getMessage().contains("unknown"));
    }

    private EffectiveRawPom effective(String xml) {
        RawPom rawPom = parser.parse(xml);
        return new EffectiveRawPom(
                rawPom,
                java.util.List.of(),
                rawPom.groupId().orElseThrow(),
                rawPom.version().orElseThrow(),
                Map.copyOf(rawPom.properties()),
                rawPom.dependencyManagement());
    }
}
