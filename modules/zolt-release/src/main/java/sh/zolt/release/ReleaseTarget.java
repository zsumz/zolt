package sh.zolt.release;

import sh.zolt.release.archive.ReleaseArchiveException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum ReleaseTarget {
    MACOS_ARM64("macos-arm64", ".tar.gz", "zolt"),
    MACOS_X64("macos-x64", ".tar.gz", "zolt"),
    LINUX_ARM64("linux-arm64", ".tar.gz", "zolt"),
    LINUX_X64("linux-x64", ".tar.gz", "zolt"),
    WINDOWS_X64("windows-x64", ".zip", "zolt.exe");

    private final String id;
    private final String archiveExtension;
    private final String binaryName;

    ReleaseTarget(String id, String archiveExtension, String binaryName) {
        this.id = id;
        this.archiveExtension = archiveExtension;
        this.binaryName = binaryName;
    }

    public String id() {
        return id;
    }

    public String archiveExtension() {
        return archiveExtension;
    }

    public String binaryName() {
        return binaryName;
    }

    public boolean zip() {
        return archiveExtension.equals(".zip");
    }

    public static ReleaseTarget fromId(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(target -> target.id.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ReleaseArchiveException(
                        "Unknown release target `" + value + "`. Supported targets: " + supportedTargets() + "."));
    }

    public static ReleaseTarget current() {
        return fromOsArch(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    public static ReleaseTarget fromOsArch(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        if ((os.contains("mac") || os.contains("darwin")) && (arch.equals("aarch64") || arch.equals("arm64"))) {
            return MACOS_ARM64;
        }
        if ((os.contains("mac") || os.contains("darwin")) && x64(arch)) {
            return MACOS_X64;
        }
        if (os.contains("linux") && arm64(arch)) {
            return LINUX_ARM64;
        }
        if (os.contains("linux") && x64(arch)) {
            return LINUX_X64;
        }
        if (os.contains("win") && x64(arch)) {
            return WINDOWS_X64;
        }
        throw new ReleaseArchiveException(
                "Could not infer release target from os.name="
                        + osName
                        + " and os.arch="
                        + osArch
                        + ". Supported release targets: "
                        + supportedTargets()
                        + ".");
    }

    public static String supportedTargets() {
        return Arrays.stream(values())
                .map(ReleaseTarget::id)
                .collect(Collectors.joining(", "));
    }

    private static boolean x64(String arch) {
        return arch.equals("x86_64") || arch.equals("amd64");
    }

    private static boolean arm64(String arch) {
        return arch.equals("aarch64") || arch.equals("arm64");
    }
}
