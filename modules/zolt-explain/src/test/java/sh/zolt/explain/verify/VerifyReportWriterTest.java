package sh.zolt.explain.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class VerifyReportWriterTest {
    private final VerifyComparator comparator = new VerifyComparator();
    private final VerifyReportJsonWriter jsonWriter = new VerifyReportJsonWriter();
    private final VerifyReportFormatter formatter = new VerifyReportFormatter();

    @Test
    void jsonIsByteIdenticalForIdenticalInputs() {
        VerifyReport report = sampleReport();

        String first = jsonWriter.json(report);
        String second = jsonWriter.json(comparator.compare(
                "/maven", "/zolt", List.of(maven()), List.of(zolt()),
                Map.of("g:a", "."), Map.of("g:a", ".")));

        assertEquals(first, second);
    }

    @Test
    void jsonCarriesStableSchemaAndDifferenceFacts() {
        String json = jsonWriter.json(sampleReport());

        assertTrue(json.contains("\"schemaVersion\": 1"), json);
        assertTrue(json.contains("\"command\": \"explain-verify\""), json);
        assertTrue(json.contains("\"result\": \"differences\""), json);
        assertTrue(json.contains("\"mavenVersion\": \"33.4.8-jre\""), json);
        assertTrue(json.contains("\"zoltVersion\": \"33.0.0-jre\""), json);
        assertTrue(json.startsWith("{\n"), json);
        assertTrue(json.endsWith("}\n"), json);
    }

    @Test
    void textReportItemizesDifferencesWithMarkers() {
        String text = formatter.text(sampleReport());

        assertTrue(text.contains("~ com.google.guava:guava  maven 33.4.8-jre  zolt 33.0.0-jre"), text);
        assertTrue(text.contains("+ org.extra:lib:1.0.0"), text);
        assertTrue(text.contains("result: differences found"), text);
        assertTrue(text.contains("version drift: 1"), text);
    }

    private VerifyReport sampleReport() {
        return comparator.compare(
                "/maven", "/zolt", List.of(maven()), List.of(zolt()),
                Map.of("g:a", "."), Map.of("g:a", "."));
    }

    private static ResolvedModule maven() {
        return new ResolvedModule("g", "a", "1.0.0", "jar", Map.of(
                VerifyScope.COMPILE, List.of(
                        artifact("com.google.guava", "guava", "33.4.8-jre"),
                        artifact("org.slf4j", "slf4j-api", "2.0.13")),
                VerifyScope.TEST, List.of(artifact("org.junit.jupiter", "junit-jupiter", "5.11.4"))),
                Map.of());
    }

    private static ResolvedModule zolt() {
        return new ResolvedModule("g", "a", "1.0.0", "jar", Map.of(
                VerifyScope.COMPILE, List.of(
                        artifact("com.google.guava", "guava", "33.0.0-jre"),
                        artifact("org.slf4j", "slf4j-api", "2.0.13"),
                        artifact("org.extra", "lib", "1.0.0")),
                VerifyScope.TEST, List.of(artifact("org.junit.jupiter", "junit-jupiter", "5.11.4"))),
                Map.of());
    }

    private static ResolvedArtifact artifact(String group, String artifact, String version) {
        return new ResolvedArtifact(group, artifact, "jar", "", version);
    }
}
