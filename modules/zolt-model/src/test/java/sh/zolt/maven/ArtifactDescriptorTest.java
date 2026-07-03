package sh.zolt.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ArtifactDescriptorTest {
    @Test
    void jarDescriptorDefaultsToJarExtensionAndOptionalClassifier() {
        Coordinate coordinate = new Coordinate("com.example", "demo", Optional.of("1.0.0"));

        ArtifactDescriptor plainJar = ArtifactDescriptor.jar(coordinate);
        ArtifactDescriptor sourcesJar = ArtifactDescriptor.jar(coordinate, Optional.of("sources"));
        ArtifactDescriptor nullClassifier = new ArtifactDescriptor(coordinate, null, "jar");

        assertEquals(coordinate, plainJar.coordinate());
        assertEquals("jar", plainJar.extension());
        assertFalse(plainJar.classifier().isPresent());
        assertEquals("sources", sourcesJar.classifier().orElseThrow());
        assertFalse(nullClassifier.classifier().isPresent());
    }

    @Test
    void rejectsMissingCoordinateClassifierAndExtension() {
        Coordinate coordinate = new Coordinate("com.example", "demo", Optional.empty());

        assertEquals(
                "Artifact descriptor requires a coordinate.",
                assertThrows(CoordinateParseException.class, () -> ArtifactDescriptor.jar(null))
                        .getMessage());
        assertEquals(
                "Artifact classifier must not be blank.",
                assertThrows(
                                CoordinateParseException.class,
                                () -> ArtifactDescriptor.jar(coordinate, Optional.of(" ")))
                        .getMessage());
        assertEquals(
                "Artifact extension is required.",
                assertThrows(
                                CoordinateParseException.class,
                                () -> new ArtifactDescriptor(coordinate, Optional.empty(), "\n"))
                        .getMessage());
    }
}
