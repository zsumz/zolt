package sh.zolt.quality;

import java.util.Locale;
import java.util.Optional;

public enum QualityCheckContext {
    LOCAL("local"),
    CI("ci");

    private final String configValue;

    QualityCheckContext(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static Optional<QualityCheckContext> fromConfigValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (QualityCheckContext context : values()) {
            if (context.configValue.equals(normalized)) {
                return Optional.of(context);
            }
        }
        return Optional.empty();
    }
}
