package sh.zolt.project;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The consumption lane an exec step's output joins. Position in the build is derived from this
 * declaration, never from an anchor: {@code JAVA_SOURCES}/{@code TEST_SOURCES} join the matching
 * compile source roots; {@code RESOURCES} joins resource copying (optionally under an {@code into}
 * subtree).
 */
public enum ProducesLane {
    JAVA_SOURCES("java-sources"),
    TEST_SOURCES("test-sources"),
    RESOURCES("resources");

    private final String configValue;

    ProducesLane(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static Optional<ProducesLane> fromConfigValue(String value) {
        return Arrays.stream(values())
                .filter(lane -> lane.configValue.equals(value))
                .findFirst();
    }

    public static String supportedValues() {
        return Arrays.stream(values())
                .map(ProducesLane::configValue)
                .collect(Collectors.joining(", "));
    }
}
