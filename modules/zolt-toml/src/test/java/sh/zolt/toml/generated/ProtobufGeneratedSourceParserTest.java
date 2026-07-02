package sh.zolt.toml.generated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.ProtobufGenerationSettings;
import sh.zolt.toml.ZoltConfigException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

final class ProtobufGeneratedSourceParserTest {

    @Test
    void parsesToolVersionAliases() {
        ProtobufGenerationSettings settings = ProtobufGeneratedSourceParser.parseTool(
                table("""
                        [generated.protobufTool]
                        protocCoordinate = "com.example:custom-protoc"
                        protocVersionRef = "protoc"
                        grpcPluginCoordinate = "com.example:custom-grpc"
                        grpcPluginVersionRef = "grpc"
                        """, "generated", "protobufTool"),
                Map.of(
                        "protoc", "4.28.3",
                        "grpc", "1.68.1"));

        assertEquals("com.example:custom-protoc", settings.protocCoordinate().orElseThrow());
        assertEquals("4.28.3", settings.protocVersion().orElseThrow());
        assertEquals("protoc", settings.protocVersionRef().orElseThrow());
        assertEquals("com.example:custom-grpc", settings.grpcPluginCoordinate().orElseThrow());
        assertEquals("1.68.1", settings.grpcPluginVersion().orElseThrow());
        assertEquals("grpc", settings.grpcPluginVersionRef().orElseThrow());
    }

    @Test
    void reportsUnknownToolVersionAlias() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () ->
                ProtobufGeneratedSourceParser.parseTool(
                        table("""
                                [generated.protobufTool]
                                protocVersionRef = "missing"
                                """, "generated", "protobufTool"),
                        Map.of()));

        assertTrue(exception.getMessage().contains("Unknown versionRef `missing`"));
        assertTrue(exception.getMessage().contains("generated.protobufTool.protocVersionRef"));
    }

    @Test
    void parsesProtobufStepWithToolDefaultsAndStepOverrides() {
        ProtobufGenerationSettings tool = new ProtobufGenerationSettings(
                java.util.Optional.of("com.google.protobuf:protoc"),
                java.util.Optional.of("4.28.3"),
                java.util.Optional.of("protoc"),
                java.util.Optional.of("io.grpc:protoc-gen-grpc-java"),
                java.util.Optional.of("1.68.1"),
                java.util.Optional.of("grpc"),
                java.util.Optional.empty(),
                true);

        var step = ProtobufGeneratedSourceParser.parseStep(
                "greeter",
                table("""
                        [generated.main.greeter]
                        kind = "protobuf"
                        language = "java"
                        output = "target/generated/sources/protobuf"
                        inputs = ["src/main/proto/greeter.proto"]
                        javaPackage = "com.example.greeter"
                        grpc = false
                        """, "generated", "main", "greeter"),
                "generated.main.greeter",
                tool);

        assertEquals("greeter", step.id());
        assertEquals(GeneratedSourceKind.PROTOBUF, step.kind());
        assertEquals(List.of("src/main/proto/greeter.proto"), step.inputs());
        assertTrue(step.clean());
        assertEquals("com.example.greeter", step.protobuf().javaPackage().orElseThrow());
        assertFalse(step.protobuf().grpc());
        assertEquals("4.28.3", step.protobuf().protocVersion().orElseThrow());
        assertEquals("1.68.1", step.protobuf().grpcPluginVersion().orElseThrow());
    }

    @Test
    void rejectsUnsupportedPluginConfiguration() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () ->
                ProtobufGeneratedSourceParser.parseStep(
                        "greeter",
                        table("""
                                [generated.main.greeter]
                                kind = "protobuf"
                                language = "java"
                                output = "target/generated/sources/protobuf"
                                inputs = ["src/main/proto/greeter.proto"]
                                plugins = { custom = "protoc-gen-custom" }
                                """, "generated", "main", "greeter"),
                        "generated.main.greeter",
                        ProtobufGenerationSettings.empty()));

        assertTrue(exception.getMessage().contains("Unsupported protoc plugin configuration"));
        assertTrue(exception.getMessage().contains("typed Java protobuf/gRPC generator"));
    }

    @Test
    void rejectsMissingInputs() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () ->
                ProtobufGeneratedSourceParser.parseStep(
                        "greeter",
                        table("""
                                [generated.main.greeter]
                                kind = "protobuf"
                                language = "java"
                                output = "target/generated/sources/protobuf"
                                inputs = []
                                """, "generated", "main", "greeter"),
                        "generated.main.greeter",
                        ProtobufGenerationSettings.empty()));

        assertTrue(exception.getMessage().contains("Add at least one project-relative .proto input path"));
    }

    @Test
    void rejectsUnsafeConfiguredJavaPackage() {
        for (String javaPackage : new String[] {
            ".tmp.pwn",
            "../pwn",
            "com..example",
            "com.example; class Evil {}",
            "com.example\nclass Evil {}"
        }) {
            ZoltConfigException exception = assertThrows(ZoltConfigException.class, () ->
                    ProtobufGeneratedSourceParser.parseStep(
                            "greeter",
                            table("""
                                    [generated.main.greeter]
                                    kind = "protobuf"
                                    language = "java"
                                    output = "target/generated/sources/protobuf"
                                    inputs = ["src/main/proto/greeter.proto"]
                                    javaPackage = "%s"
                                    """.formatted(tomlString(javaPackage)), "generated", "main", "greeter"),
                            "generated.main.greeter",
                            ProtobufGenerationSettings.empty()));

            assertTrue(exception.getMessage().contains("[generated.main.greeter].javaPackage Java package"));
            assertTrue(exception.getMessage().contains("[A-Za-z_$][A-Za-z0-9_$]*"));
            assertTrue(!exception.getMessage().contains("Evil"), exception.getMessage());
        }
    }

    private static TomlTable table(String content, String... path) {
        TomlTable table = Toml.parse(content);
        for (String key : path) {
            table = table.getTable(List.of(key));
        }
        return table;
    }

    private static String tomlString(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
