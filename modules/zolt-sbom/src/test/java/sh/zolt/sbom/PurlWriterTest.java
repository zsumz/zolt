package sh.zolt.sbom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PurlWriterTest {
    @Test
    void buildsBareMavenPurlWithTypeQualifier() {
        assertEquals(
                "pkg:maven/org.example/lib-a@1.0.0?type=jar",
                PurlWriter.purl("org.example", "lib-a", "1.0.0", "jar", Optional.empty()));
    }

    @Test
    void ordersClassifierBeforeTypeForCanonicalForm() {
        assertEquals(
                "pkg:maven/io.netty/netty-transport@4.1.0?classifier=linux-x86_64&type=jar",
                PurlWriter.purl("io.netty", "netty-transport", "4.1.0", "jar", Optional.of("linux-x86_64")));
    }

    @Test
    void percentEncodesReservedCharactersInPathAndQualifiers() {
        assertEquals(
                "pkg:maven/com.example/tool@1.0%2Bbuild%205?classifier=with%20space&type=jar",
                PurlWriter.purl("com.example", "tool", "1.0+build 5", "jar", Optional.of("with space")));
    }

    @Test
    void treatsBlankClassifierAsAbsent() {
        assertEquals(
                "pkg:maven/g/a@1?type=jar",
                PurlWriter.purl("g", "a", "1", "jar", Optional.of("  ")));
    }
}
