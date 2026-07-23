package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class MavenRepositoryClientMetadataTest extends MavenRepositoryClientTestSupport {
    private static final String METADATA_XML =
            """
            <metadata>
              <groupId>com.google.guava</groupId>
              <artifactId>guava</artifactId>
              <versioning>
                <versions>
                  <version>32.1.3-jre</version>
                  <version>33.4.0-jre</version>
                </versions>
              </versioning>
            </metadata>
            """;

    @Test
    void buildsMetadataPathWithoutVersionSegment() {
        assertEquals(
                "com/google/guava/guava/maven-metadata.xml",
                new MavenRepositoryPathBuilder().metadataPath("com.google.guava", "guava"));
    }

    @Test
    void fetchMetadataReturnsListingBytes() {
        put("com/google/guava/guava/maven-metadata.xml", METADATA_XML);

        Optional<byte[]> metadata =
                client.fetchMetadata(baseUri, "com.google.guava", "guava", RepositoryAuthentication.none());

        assertTrue(metadata.isPresent());
        assertTrue(new String(metadata.orElseThrow(), StandardCharsets.UTF_8).contains("33.4.0-jre"));
        assertEquals(1, requestCount("/maven2/com/google/guava/guava/maven-metadata.xml"));
    }

    @Test
    void fetchMetadataReturnsEmptyWhenListingMissing() {
        Optional<byte[]> metadata =
                client.fetchMetadata(baseUri, "com.google.guava", "absent", RepositoryAuthentication.none());

        assertFalse(metadata.isPresent());
        assertEquals(1, requestCount("/maven2/com/google/guava/absent/maven-metadata.xml"));
    }

    @Test
    void fetchMetadataRetriesTransientFailures() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/retry/com/example/lib/maven-metadata.xml", exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, "unavailable".getBytes(StandardCharsets.UTF_8));
                return;
            }
            respond(exchange, 200, METADATA_XML.getBytes(StandardCharsets.UTF_8));
        });
        URI retryBaseUri = URI.create(baseUri.toString().replace("/maven2/", "/retry/"));

        Optional<byte[]> metadata = retryingClient(3)
                .fetchMetadata(retryBaseUri, "com.example", "lib", RepositoryAuthentication.none());

        assertTrue(metadata.isPresent());
        assertEquals(2, attempts.get());
    }
}
