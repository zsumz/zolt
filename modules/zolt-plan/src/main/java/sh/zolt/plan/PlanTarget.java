package sh.zolt.plan;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public enum PlanTarget {
    BUILD("build"),
    TEST("test"),
    PACKAGE("package"),
    NATIVE("native"),
    CI("ci");

    private final String configValue;

    PlanTarget(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public boolean includesTests() {
        return this == TEST || this == CI;
    }

    public boolean includesCoverage() {
        return this == CI;
    }

    public boolean includesPackage() {
        return this == PACKAGE || this == CI;
    }

    public boolean includesPublish() {
        return this == CI;
    }

    public static Optional<PlanTarget> fromConfigValue(String value) {
        return Arrays.stream(values())
                .filter(target -> target.configValue.equals(value))
                .findFirst();
    }

    public static String supportedValues() {
        return Arrays.stream(values())
                .map(PlanTarget::configValue)
                .collect(Collectors.joining(", "));
    }
}
