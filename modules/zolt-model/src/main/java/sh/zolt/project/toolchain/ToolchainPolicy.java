package sh.zolt.project.toolchain;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public enum ToolchainPolicy {
    PREFER_MANAGED("prefer-managed"),
    REQUIRE_MANAGED("require-managed"),
    ALLOW_SYSTEM("allow-system");

    private final String id;

    ToolchainPolicy(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<ToolchainPolicy> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalized = id.strip();
        return Arrays.stream(values())
                .filter(policy -> policy.id.equals(normalized))
                .findFirst();
    }

    public static String supportedIds() {
        return Arrays.stream(values())
                .map(ToolchainPolicy::id)
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
