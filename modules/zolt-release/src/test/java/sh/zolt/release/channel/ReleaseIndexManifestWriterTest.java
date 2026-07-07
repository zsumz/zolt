package sh.zolt.release.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ReleaseIndexManifestWriterTest {
    private final ReleaseIndexManifestValidator validator = new ReleaseIndexManifestValidator();
    private final ReleaseIndexManifestWriter writer = new ReleaseIndexManifestWriter();

    @Test
    void writesDeterministicJsonThatRoundTripsThroughValidator() {
        ReleaseIndexManifest manifest = validator.validate(ReleaseIndexManifestValidatorTest.indexJson(
                ReleaseIndexManifestValidatorTest.versionJson("0.1.0-zap.20260706.222222222222")));

        String json = writer.write(manifest);
        ReleaseIndexManifest reparsed = validator.validate(json);

        assertTrue(json.startsWith("{\n  \"schemaVersion\": 1,"));
        assertTrue(json.contains("\"versions\": ["));
        assertEquals(manifest, reparsed);
    }
}
