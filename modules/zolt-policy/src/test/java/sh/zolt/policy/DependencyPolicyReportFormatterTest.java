package sh.zolt.policy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DependencyPolicyReportFormatterTest {
    @Test
    void jsonPrintsNullVersionRefForLiteralPlatforms() {
        DependencyPolicyReport report = new DependencyPolicyReport(
                Path.of("/project"),
                List.of(new DependencyPolicyReport.PlatformPolicyDiagnostic(
                        "com.acme:enterprise-platform:2026.1.0",
                        Optional.empty(),
                        List.of())),
                List.of(),
                List.of(),
                List.of());

        String json = new DependencyPolicyReportFormatter().json(report);

        assertTrue(json.contains("\"platform\": \"com.acme:enterprise-platform:2026.1.0\""));
        assertTrue(json.contains("\"versionRef\": null"));
    }
}
