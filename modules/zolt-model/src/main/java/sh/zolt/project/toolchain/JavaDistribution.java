package sh.zolt.project.toolchain;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public enum JavaDistribution {
    GRAALVM_COMMUNITY("graalvm-community"),
    TEMURIN("temurin");

    private final String id;

    JavaDistribution(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<JavaDistribution> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalized = id.strip();
        return Arrays.stream(values())
                .filter(distribution -> distribution.id.equals(normalized))
                .findFirst();
    }

    public static String supportedIds() {
        return Arrays.stream(values())
                .map(JavaDistribution::id)
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
