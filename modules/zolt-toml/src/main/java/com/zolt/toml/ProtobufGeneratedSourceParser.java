package com.zolt.toml;

import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.ProtobufGenerationSettings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.tomlj.TomlTable;

final class ProtobufGeneratedSourceParser {
    private static final Set<String> GENERATED_PROTOBUF_SOURCE_KEYS =
            Set.of("kind", "language", "output", "inputs", "required", "clean", "javaPackage", "grpc", "plugin", "plugins");
    private static final Set<String> PROTOBUF_TOOL_KEYS = Set.of(
            "protocCoordinate",
            "protocVersion",
            "protocVersionRef",
            "grpcPluginCoordinate",
            "grpcPluginVersion",
            "grpcPluginVersionRef");

    private ProtobufGeneratedSourceParser() {
    }

    static GeneratedSourceStep parseStep(
            String id,
            TomlTable table,
            String section,
            ProtobufGenerationSettings tool) {
        TomlValidation.validateKeysWithVersionRefHint(section, table, GENERATED_PROTOBUF_SOURCE_KEYS);
        if (table.contains("plugin") || table.contains("plugins")) {
            throw new ZoltConfigException(
                    "Unsupported protoc plugin configuration in ["
                            + section
                            + "]. Zolt supports only the typed Java protobuf/gRPC generator in the public beta; "
                            + "remove plugin/plugins or add a dedicated Zolt generator followUp.");
        }
        List<String> inputs = TomlScalars.stringListOrDefault(table, section, "inputs", List.of());
        if (inputs.isEmpty()) {
            throw new ZoltConfigException(
                    "Missing required field ["
                            + section
                            + "].inputs in zolt.toml. Add at least one project-relative .proto input path.");
        }
        ProtobufGenerationSettings settings = new ProtobufGenerationSettings(
                tool.protocCoordinate(),
                tool.protocVersion(),
                tool.protocVersionRef(),
                tool.grpcPluginCoordinate(),
                tool.grpcPluginVersion(),
                tool.grpcPluginVersionRef(),
                TomlScalars.optionalString(table, section, "javaPackage"),
                TomlScalars.booleanOrDefault(table, section, "grpc", true));
        return new GeneratedSourceStep(
                id,
                GeneratedSourceKind.PROTOBUF,
                TomlScalars.requiredString(table, section, "language"),
                TomlScalars.requiredString(table, section, "output"),
                inputs,
                TomlScalars.booleanOrDefault(table, section, "required", true),
                TomlScalars.booleanOrDefault(table, section, "clean", true),
                OpenApiGenerationSettings.empty(),
                settings);
    }

    static ProtobufGenerationSettings parseTool(
            TomlTable table,
            Map<String, String> versionAliases) {
        if (table == null) {
            return ProtobufGenerationSettings.empty();
        }
        TomlValidation.validateKeysWithVersionRefHint("generated.protobufTool", table, PROTOBUF_TOOL_KEYS);
        Optional<String> protocVersionRef = TomlScalars.optionalString(table, "generated.protobufTool", "protocVersionRef");
        Optional<String> grpcPluginVersionRef = TomlScalars.optionalString(table, "generated.protobufTool", "grpcPluginVersionRef");
        Optional<String> protocVersion = versionFromAliasOrField(
                "generated.protobufTool.protocVersionRef",
                protocVersionRef,
                TomlScalars.optionalString(table, "generated.protobufTool", "protocVersion"),
                versionAliases);
        Optional<String> grpcPluginVersion = versionFromAliasOrField(
                "generated.protobufTool.grpcPluginVersionRef",
                grpcPluginVersionRef,
                TomlScalars.optionalString(table, "generated.protobufTool", "grpcPluginVersion"),
                versionAliases);
        return new ProtobufGenerationSettings(
                Optional.of(TomlScalars.stringOrDefault(
                        table,
                        "generated.protobufTool",
                        "protocCoordinate",
                        "com.google.protobuf:protoc")),
                protocVersion,
                protocVersionRef,
                Optional.of(TomlScalars.stringOrDefault(
                        table,
                        "generated.protobufTool",
                        "grpcPluginCoordinate",
                        "io.grpc:protoc-gen-grpc-java")),
                grpcPluginVersion,
                grpcPluginVersionRef,
                Optional.empty(),
                true);
    }

    private static Optional<String> versionFromAliasOrField(
            String subject,
            Optional<String> versionRef,
            Optional<String> version,
            Map<String, String> versionAliases) {
        if (versionRef.isEmpty()) {
            return version;
        }
        String alias = versionRef.orElseThrow();
        String resolved = versionAliases.get(alias);
        if (resolved == null || resolved.isBlank()) {
            throw new ZoltConfigException(
                    "Unknown versionRef `"
                            + alias
                            + "` for "
                            + subject
                            + ". Add it to [versions] or use an explicit version.");
        }
        return Optional.of(resolved);
    }
}
