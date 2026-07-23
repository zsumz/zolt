package sh.zolt.project;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * How the license policy gate treats a dependency whose license is UNKNOWN (no license found in the
 * cached POM chain). Defaults to {@link #WARN}: failing on UNKNOWN by default would break most real
 * projects, so strict shops opt into {@link #FAIL} in CI.
 */
public enum UnknownLicensePolicy {
    FAIL("fail"),
    WARN("warn"),
    ALLOW("allow");

    private final String configValue;

    UnknownLicensePolicy(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static Optional<UnknownLicensePolicy> fromConfigValue(String value) {
        return Arrays.stream(values())
                .filter(policy -> policy.configValue.equals(value))
                .findFirst();
    }

    public static String supportedValues() {
        return Arrays.stream(values())
                .map(UnknownLicensePolicy::configValue)
                .collect(Collectors.joining(", "));
    }
}
