package sh.zolt.toolchain.platform;

import sh.zolt.error.ActionableException;
import java.util.Locale;

public enum Architecture {
    X64("x64"),
    AARCH64("aarch64");

    private final String id;

    Architecture(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    static Architecture fromSystemName(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (normalized.equals("x86_64") || normalized.equals("amd64") || normalized.equals("x64")) {
            return X64;
        }
        if (normalized.equals("aarch64") || normalized.equals("arm64")) {
            return AARCH64;
        }
        throw new ActionableException(
                "Unsupported Java toolchain architecture `" + value + "`.",
                "Use x64 or aarch64.");
    }

    static Architecture fromId(String value) {
        return switch (value) {
            case "x64", "x86_64", "amd64" -> X64;
            case "aarch64", "arm64" -> AARCH64;
            default -> throw new ActionableException(
                    "Unsupported Java toolchain architecture `" + value + "`.",
                    "Use x64 or aarch64.");
        };
    }
}
