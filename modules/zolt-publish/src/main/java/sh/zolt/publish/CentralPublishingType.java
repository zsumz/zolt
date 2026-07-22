package sh.zolt.publish;

import java.util.Optional;

/**
 * The Sonatype Central Portal {@code publishingType} for a deployment. {@link #USER_MANAGED} (the
 * Portal default) validates the bundle and waits for a manual publish; {@link #AUTOMATIC} publishes
 * to Maven Central automatically once validation passes.
 */
public enum CentralPublishingType {
    USER_MANAGED("user-managed", "USER_MANAGED"),
    AUTOMATIC("automatic", "AUTOMATIC");

    private final String configValue;
    private final String apiValue;

    CentralPublishingType(String configValue, String apiValue) {
        this.configValue = configValue;
        this.apiValue = apiValue;
    }

    public String configValue() {
        return configValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static Optional<CentralPublishingType> fromConfigValue(String value) {
        for (CentralPublishingType type : values()) {
            if (type.configValue.equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static String supportedValues() {
        return "user-managed, automatic";
    }
}
