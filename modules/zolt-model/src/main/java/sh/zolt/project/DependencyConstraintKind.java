package sh.zolt.project;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public enum DependencyConstraintKind {
    STRICT("strict");

    private final String configValue;

    DependencyConstraintKind(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static Optional<DependencyConstraintKind> fromConfigValue(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.configValue.equals(value))
                .findFirst();
    }

    public static String supportedValues() {
        return Arrays.stream(values())
                .map(DependencyConstraintKind::configValue)
                .collect(Collectors.joining(", "));
    }
}
