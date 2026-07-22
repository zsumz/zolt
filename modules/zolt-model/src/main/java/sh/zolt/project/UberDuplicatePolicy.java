package sh.zolt.project;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public enum UberDuplicatePolicy {
    FAIL("fail"),
    FIRST_WINS("first-wins");

    private final String configValue;

    UberDuplicatePolicy(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static Optional<UberDuplicatePolicy> fromConfigValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (UberDuplicatePolicy policy : values()) {
            if (policy.configValue.equals(value)) {
                return Optional.of(policy);
            }
        }
        return Optional.empty();
    }

    public static String supportedValues() {
        return Arrays.stream(values())
                .map(UberDuplicatePolicy::configValue)
                .collect(Collectors.joining(", "));
    }
}
