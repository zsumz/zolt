package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PublishUploadFormatterTest {
    @Test
    void rendersUploadedReportWithSupplementalArtifacts() {
        PublishArtifactPlan sources = new PublishArtifactPlan(
                "sources",
                Optional.of("sources"),
                Path.of("/work/target/app-1.0.0-sources.jar"),
                "sha256:aaa",
                "com/example/app/1.0.0/app-1.0.0-sources.jar");
        PublishArtifactPlan javadoc = new PublishArtifactPlan(
                "javadoc",
                Optional.of("javadoc"),
                Path.of("/work/target/app-1.0.0-javadoc.jar"),
                "sha256:bbb",
                "com/example/app/1.0.0/app-1.0.0-javadoc.jar");
        PublishDryRunPlan plan = new PublishDryRunPlan(
                "com.example:app:1.0.0",
                "release",
                "company-releases",
                "https://repo.example.test/releases",
                "main",
                Path.of("/work/target/app-1.0.0.jar"),
                "sha256:main",
                "com/example/app/1.0.0/app-1.0.0.jar",
                List.of(sources, javadoc),
                Path.of("/work/target/evidence.json"),
                Path.of("/work/target/app-1.0.0.pom"),
                "sha256:pom",
                "com/example/app/1.0.0/app-1.0.0.pom",
                "release",
                List.of());

        String text = PublishUploadFormatter.text(new PublishUploadResult(plan));

        assertEquals("""
                Zolt publish
                Coordinate: com.example:app:1.0.0
                Target repository: company-releases
                Artifact uploaded: com/example/app/1.0.0/app-1.0.0.jar
                Supplemental artifact uploaded: com/example/app/1.0.0/app-1.0.0-sources.jar
                Supplemental artifact uploaded: com/example/app/1.0.0/app-1.0.0-javadoc.jar
                POM uploaded: com/example/app/1.0.0/app-1.0.0.pom
                Status: uploaded
                """, text);
    }
}
