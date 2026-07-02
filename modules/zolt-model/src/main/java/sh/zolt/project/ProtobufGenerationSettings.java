package sh.zolt.project;

import java.util.Optional;

public record ProtobufGenerationSettings(
        Optional<String> protocCoordinate,
        Optional<String> protocVersion,
        Optional<String> protocVersionRef,
        Optional<String> grpcPluginCoordinate,
        Optional<String> grpcPluginVersion,
        Optional<String> grpcPluginVersionRef,
        Optional<String> javaPackage,
        boolean grpc) {
    public ProtobufGenerationSettings {
        protocCoordinate = protocCoordinate == null ? Optional.empty() : protocCoordinate;
        protocVersion = protocVersion == null ? Optional.empty() : protocVersion;
        protocVersionRef = protocVersionRef == null ? Optional.empty() : protocVersionRef;
        grpcPluginCoordinate = grpcPluginCoordinate == null ? Optional.empty() : grpcPluginCoordinate;
        grpcPluginVersion = grpcPluginVersion == null ? Optional.empty() : grpcPluginVersion;
        grpcPluginVersionRef = grpcPluginVersionRef == null ? Optional.empty() : grpcPluginVersionRef;
        javaPackage = javaPackage == null ? Optional.empty() : javaPackage;
    }

    public static ProtobufGenerationSettings empty() {
        return new ProtobufGenerationSettings(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true);
    }
}
