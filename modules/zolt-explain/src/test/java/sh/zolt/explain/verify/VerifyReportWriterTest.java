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
                BuildTool.MAVEN, "/maven", "/zolt", List.of(maven()), List.of(zolt()),
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

    @Test
    void mavenReportsDeclareTheirBuildToolInJson() {
        assertTrue(jsonWriter.json(sampleReport()).contains("\"buildTool\": \"maven\""), "maven buildTool");
    }

    @Test
    void gradleBuildToolRelabelsIncumbentSideButKeepsJsonFieldNames() {
        VerifyReport gradle = comparator.compare(
                BuildTool.GRADLE, "/gradle", "/zolt", List.of(maven()), List.of(zolt()),
                Map.of("g:a", "app"), Map.of("g:a", "."));

        String json = jsonWriter.json(gradle);
        assertTrue(json.contains("\"buildTool\": \"gradle\""), json);
        // Field names stay maven* for schema stability; only the additive buildTool field disambiguates.
        assertTrue(json.contains("\"mavenRoot\": \"/gradle\""), json);
        assertTrue(json.contains("\"mavenVersion\": \"33.4.8-jre\""), json);

        String text = formatter.text(gradle);
        assertTrue(text.contains("Gradle vs Zolt resolved dependencies"), text);
        assertTrue(text.contains("Gradle root: /gradle"), text);
        assertTrue(text.contains("only in gradle:"), text);
        assertTrue(text.contains("  gradle 33.4.8-jre  zolt 33.0.0-jre"), text);
    }

    private VerifyReport sampleReport() {
        return comparator.compare(
                BuildTool.MAVEN, "/maven", "/zolt", List.of(maven()), List.of(zolt()),
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
