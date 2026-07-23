package sh.zolt.maven.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class MavenMetadataParserTest {
    private final MavenMetadataParser parser = new MavenMetadataParser();

    @Test
    void parsesVersionsInDocumentOrder() {
        MavenMetadata metadata = parser.parse(
                """
                <metadata>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <versioning>
                    <versions>
                      <version>1.0.0</version>
                      <version>1.1.0</version>
                      <version>2.0.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """);

        assertEquals(List.of("1.0.0", "1.1.0", "2.0.0"), metadata.versions());
    }

    @Test
    void ignoresLatestAndReleaseHints() {
        MavenMetadata metadata = parser.parse(
                """
                <metadata>
                  <versioning>
                    <latest>9.9.9</latest>
                    <release>9.9.9</release>
                    <versions>
                      <version>1.0.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """);

        assertEquals(List.of("1.0.0"), metadata.versions());
    }

    @Test
    void returnsEmptyWhenVersioningAbsent() {
        MavenMetadata metadata = parser.parse("<metadata><groupId>g</groupId></metadata>");
        assertTrue(metadata.versions().isEmpty());
    }

    @Test
    void rejectsWrongRootElement() {
        MavenMetadataParseException exception =
                assertThrows(MavenMetadataParseException.class, () -> parser.parse("<project></project>"));
        assertTrue(exception.getMessage().contains("Expected root <metadata>"));
    }

    @Test
    void rejectsMalformedXml() {
        assertThrows(MavenMetadataParseException.class, () -> parser.parse("<metadata><versioning>"));
    }

    @Test
    void rejectsDoctypeDeclaration() {
        String hostileXml =
                """
                <?xml version="1.0"?>
                <!DOCTYPE metadata [ <!ENTITY payload "expanded"> ]>
                <metadata>
                  <versioning><versions><version>&payload;</version></versions></versioning>
                </metadata>
                """;
        assertThrows(MavenMetadataParseException.class, () -> parser.parse(hostileXml));
    }
}
