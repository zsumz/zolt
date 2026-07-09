package sh.zolt.toolchain.platform;

import sh.zolt.error.ActionableException;
import java.util.Locale;

public enum OperatingSystem {
    LINUX("linux"),
    MACOS("macos"),
    WINDOWS("windows");

    private final String id;

    OperatingSystem(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    static OperatingSystem fromSystemName(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (normalized.contains("linux")) {
            return LINUX;
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return MACOS;
        }
        if (normalized.contains("win")) {
            return WINDOWS;
        }
        throw new ActionableException(
                "Unsupported Java toolchain operating system `" + value + "`.",
                "Use a system supported by Zolt toolchain sync or pass a supported hidden test target.");
    }

    static OperatingSystem fromId(String value) {
        return switch (value) {
            case "linux" -> LINUX;
            case "macos", "darwin" -> MACOS;
            case "windows", "win" -> WINDOWS;
            default -> throw new ActionableException(
                    "Unsupported Java toolchain operating system `" + value + "`.",
                    "Use linux, macos, or windows.");
        };
    }
}
