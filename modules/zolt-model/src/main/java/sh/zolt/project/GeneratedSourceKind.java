package sh.zolt.project;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public enum GeneratedSourceKind {
    DECLARED_ROOT("declared-root"),
    OPENAPI("openapi"),
    PROTOBUF("protobuf"),
    EXEC("exec");

    private final String configValue;

    GeneratedSourceKind(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static Optional<GeneratedSourceKind> fromConfigValue(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.configValue.equals(value))
                .findFirst();
    }

    public static String supportedValues() {
        return Arrays.stream(values())
                .map(GeneratedSourceKind::configValue)
                .collect(Collectors.joining(", "));
    }
}
