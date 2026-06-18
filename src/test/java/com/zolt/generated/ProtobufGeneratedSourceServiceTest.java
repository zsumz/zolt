package com.zolt.generated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.BuildException;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtobufGeneratedSourceServiceTest {
    @TempDir
    private Path tempDir;

    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ProtobufGeneratedSourceService service = new ProtobufGeneratedSourceService();

    @Test
    void generatesDeterministicJavaSourcesForMessagesAndGrpcServices() throws IOException {
        Path proto = tempDir.resolve("src/main/proto/greeter.proto");
        Files.createDirectories(proto.getParent());
        Files.writeString(proto, """
                syntax = "proto3";
                package com.example.greeter;
                option java_package = "com.example.greeter.api";

                message HelloReply {
                  string message = 1;
                }

                message HelloRequest {
                  string name = 1;
                }

                service Greeter {
                  rpc SayHello (HelloRequest) returns (HelloReply);
                }
                """);
        var config = parser.parse(config("""
                [generated.main.greeter]
                kind = "protobuf"
                language = "java"
                inputs = ["src/main/proto/greeter.proto"]
                output = "target/generated/sources/protobuf"
                """));

        service.generateMain(tempDir, config);

        Path output = tempDir.resolve("target/generated/sources/protobuf/com/example/greeter/api");
        assertTrue(Files.exists(output.resolve("HelloReply.java")));
        assertTrue(Files.exists(output.resolve("HelloRequest.java")));
        assertTrue(Files.exists(output.resolve("GreeterGrpc.java")));
        assertTrue(Files.readString(output.resolve("GreeterGrpc.java"))
                .contains("return \"com.example.greeter.Greeter\";"));
        assertTrue(Files.readString(tempDir.resolve("target/generated/sources/protobuf/META-INF/zolt/protobuf/greeter.descriptor"))
                .contains("messages=HelloReply,HelloRequest"));
    }

    @Test
    void parserRejectsArbitraryProtocPlugins() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse(config("""
                        [generated.main.greeter]
                        kind = "protobuf"
                        language = "java"
                        inputs = ["src/main/proto/greeter.proto"]
                        output = "target/generated/sources/protobuf"
                        plugins = { custom = "protoc-gen-custom" }
                        """)));

        assertTrue(exception.getMessage().contains("Unsupported protoc plugin configuration"));
        assertTrue(exception.getMessage().contains("typed Java protobuf/gRPC generator"));
    }

    @Test
    void generationFailsClearlyForMissingInputs() {
        var config = parser.parse(config("""
                [generated.main.greeter]
                kind = "protobuf"
                language = "java"
                inputs = ["src/main/proto/missing.proto"]
                output = "target/generated/sources/protobuf"
                """));

        BuildException exception = assertThrows(BuildException.class, () -> service.generateMain(tempDir, config));

        assertTrue(exception.getMessage().contains("Protobuf input src/main/proto/missing.proto does not exist"));
        assertTrue(exception.getMessage().contains("[generated.main.greeter]"));
    }

    @Test
    void parserRecordsProtobufKindAndSettings() {
        var config = parser.parse(config("""
                [versions]
                protoc = "4.28.3"
                grpc = "1.68.1"

                [generated.protobufTool]
                protocVersionRef = "protoc"
                grpcPluginVersionRef = "grpc"

                [generated.main.greeter]
                kind = "protobuf"
                language = "java"
                inputs = ["src/main/proto/greeter.proto"]
                output = "target/generated/sources/protobuf"
                javaPackage = "com.example.greeter"
                grpc = false
                """));

        var step = config.build().generatedMainSources().getFirst();
        assertEquals(GeneratedSourceKind.PROTOBUF, step.kind());
        assertEquals("com.google.protobuf:protoc", step.protobuf().protocCoordinate().orElseThrow());
        assertEquals("4.28.3", step.protobuf().protocVersion().orElseThrow());
        assertEquals("protoc", step.protobuf().protocVersionRef().orElseThrow());
        assertEquals("io.grpc:protoc-gen-grpc-java", step.protobuf().grpcPluginCoordinate().orElseThrow());
        assertEquals("1.68.1", step.protobuf().grpcPluginVersion().orElseThrow());
        assertEquals("grpc", step.protobuf().grpcPluginVersionRef().orElseThrow());
        assertEquals("com.example.greeter", step.protobuf().javaPackage().orElseThrow());
        assertEquals(false, step.protobuf().grpc());
    }

    private static String config(String generated) {
        return """
                [project]
                name = "protobuf-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                %s
                """.formatted(generated);
    }
}
