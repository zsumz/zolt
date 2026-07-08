package sh.zolt.toolchain.platform;

import sh.zolt.error.ActionableException;
import java.util.Locale;

public record HostPlatform(
        OperatingSystem os,
        Architecture arch) {
    public HostPlatform {
        if (os == null) {
            throw new IllegalArgumentException("Host operating system is required.");
        }
        if (arch == null) {
            throw new IllegalArgumentException("Host architecture is required.");
        }
    }

    public static HostPlatform current() {
        return new HostPlatform(
                OperatingSystem.fromSystemName(System.getProperty("os.name")),
                Architecture.fromSystemName(System.getProperty("os.arch")));
    }

    public static HostPlatform parse(String value) {
        if (value == null || value.isBlank()) {
            return current();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        String[] parts = normalized.split("-", -1);
        if (parts.length != 2) {
            throw new ActionableException(
                    "Invalid toolchain target `" + value + "`.",
                    "Use a target such as linux-x64, linux-aarch64, macos-x64, macos-aarch64, or windows-x64.");
        }
        return new HostPlatform(OperatingSystem.fromId(parts[0]), Architecture.fromId(parts[1]));
    }

    public String id() {
        return os.id() + "-" + arch.id();
    }
}
