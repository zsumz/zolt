package sh.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.BomSettings;
import sh.zolt.project.PackageMode;
import sh.zolt.project.ProjectConfig;
import org.junit.jupiter.api.Test;

final class ZoltTomlBomParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void bomSectionImpliesBomModeAndResolvesVersionsAndImports() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "platform-bom"
                version = "1.4.0"
                group = "com.acme.platform"
                java = "21"

                [versions]
                netty = "4.1.100.Final"
                jackson = "2.17.0"

                [bom]
                members = true

                [bom.versions]
                "org.postgresql:postgresql" = "42.7.4"
                "io.netty:netty-transport-native-epoll" = { versionRef = "netty", classifier = "linux-x86_64" }

                [bom.imports]
                "com.fasterxml.jackson:jackson-bom" = { versionRef = "jackson" }

                [package.metadata]
                name = "Acme Platform BOM"
                """);

        assertEquals(PackageMode.BOM, config.packageSettings().mode());
        BomSettings bom = config.packageSettings().bom();
        assertTrue(bom.members().all());
        assertEquals("Acme Platform BOM", config.packageSettings().metadata().name());

        BomSettings.ManagedVersion postgres = version(bom, "org.postgresql:postgresql");
        assertEquals("42.7.4", postgres.version());
        assertNull(postgres.classifier());
        BomSettings.ManagedVersion netty = version(bom, "io.netty:netty-transport-native-epoll");
        assertEquals("4.1.100.Final", netty.version());
        assertEquals("netty", netty.versionRef());
        assertEquals("linux-x86_64", netty.classifier());

        BomSettings.ImportedBom jackson = bom.imports().get(0);
        assertEquals("com.fasterxml.jackson:jackson-bom", jackson.coordinate());
        assertEquals("2.17.0", jackson.version());
        assertEquals("jackson", jackson.versionRef());
    }

    @Test
    void explicitBomModeIsASynonymForTheBomSection() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "platform-bom"
                version = "1.4.0"
                group = "com.acme.platform"
                java = "21"

                [package]
                mode = "bom"

                [bom]
                members = ["core", "http"]
                exclude = ["legacy"]
                """);

        assertEquals(PackageMode.BOM, config.packageSettings().mode());
        assertEquals(java.util.List.of("core", "http"), config.packageSettings().bom().members().paths());
        assertEquals(java.util.List.of("legacy"), config.packageSettings().bom().members().exclude());
    }

    @Test
    void bomModeWithoutBomSectionIsAConfigError() {
        ZoltConfigException error = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "app"
                version = "1.0.0"
                group = "com.example"
                java = "21"

                [package]
                mode = "bom"
                """));
        assertTrue(error.getMessage().contains("[bom] section"));
    }

    @Test
    void anotherModeAlongsideBomSectionIsAConfigError() {
        ZoltConfigException error = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "app"
                version = "1.0.0"
                group = "com.example"
                java = "21"

                [package]
                mode = "uber"

                [bom]
                members = true
                """));
        assertTrue(error.getMessage().contains("implies package mode `bom`"));
    }

    @Test
    void dependencySectionOnABomIsAConfigError() {
        ZoltConfigException error = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "platform-bom"
                version = "1.0.0"
                group = "com.acme.platform"
                java = "21"

                [bom]
                members = true

                [dependencies]
                "org.slf4j:slf4j-api" = "2.0.13"
                """));
        assertTrue(error.getMessage().contains("BOM"));
    }

    @Test
    void sourcesUnderABomIsAConfigError() {
        ZoltConfigException error = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "platform-bom"
                version = "1.0.0"
                group = "com.acme.platform"
                java = "21"

                [package]
                sources = true

                [bom]
                members = true
                """));
        assertTrue(error.getMessage().contains("[package].sources"));
    }

    @Test
    void bomSettingsRoundTripThroughTheWriter() {
        String source = """
                [project]
                name = "platform-bom"
                version = "1.4.0"
                group = "com.acme.platform"
                java = "21"

                [bom]
                members = true

                [bom.versions]
                "org.postgresql:postgresql" = "42.7.4"

                [bom.imports]
                "com.fasterxml.jackson:jackson-bom" = "2.17.0"
                """;

        ProjectConfig config = parser.parse(source);
        ProjectConfig reparsed = parser.parse(writer.write(config));

        assertEquals(PackageMode.BOM, reparsed.packageSettings().mode());
        assertTrue(reparsed.packageSettings().bom().members().all());
        assertEquals("42.7.4", version(reparsed.packageSettings().bom(), "org.postgresql:postgresql").version());
        assertEquals("2.17.0", reparsed.packageSettings().bom().imports().get(0).version());
    }

    private static BomSettings.ManagedVersion version(BomSettings bom, String coordinate) {
        return bom.versions().stream()
                .filter(version -> version.coordinate().equals(coordinate))
                .findFirst()
                .orElseThrow();
    }
}
