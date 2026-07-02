package sh.zolt.project;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public enum QuarkusPackageMode {
    FAST_JAR("fast-jar");

    private final String configValue;

    QuarkusPackageMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static Optional<QuarkusPackageMode> fromConfigValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (QuarkusPackageMode mode : values()) {
            if (mode.configValue.equals(value)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    public static String supportedValues() {
        return Arrays.stream(values())
                .map(QuarkusPackageMode::configValue)
                .collect(Collectors.joining(", "));
    }
}
