package sh.zolt.toolchain.catalog;

import sh.zolt.project.toolchain.JavaDistribution;
import sh.zolt.project.toolchain.JavaFeature;
import sh.zolt.project.toolchain.JavaToolchainRequest;
import sh.zolt.toolchain.lock.JavaToolchainLayout;
import sh.zolt.toolchain.lock.LockedJavaToolchain;
import sh.zolt.toolchain.platform.Architecture;
import sh.zolt.toolchain.platform.HostPlatform;
import sh.zolt.toolchain.platform.OperatingSystem;
import java.net.URI;
import java.util.List;
import java.util.Optional;

public final class BundledJavaToolchainCatalog implements JavaToolchainCatalog {
    private static final String GRAALVM_JDK_21 = "21.0.2";
    private static final List<HostPlatform> SUPPORTED_PLATFORMS = List.of(
            new HostPlatform(OperatingSystem.LINUX, Architecture.X64),
            new HostPlatform(OperatingSystem.LINUX, Architecture.AARCH64),
            new HostPlatform(OperatingSystem.MACOS, Architecture.X64),
            new HostPlatform(OperatingSystem.MACOS, Architecture.AARCH64));

    @Override
    public Optional<LockedJavaToolchain> lock(JavaToolchainRequest request, HostPlatform platform) {
        if (!"21".equals(request.version()) || request.distribution().isEmpty()) {
            return Optional.empty();
        }
        if (!SUPPORTED_PLATFORMS.contains(platform)) {
            return Optional.empty();
        }
        JavaDistribution distribution = request.distribution().orElseThrow();
        if (request.requiresNativeImage() && distribution != JavaDistribution.GRAALVM_COMMUNITY) {
            return Optional.empty();
        }
        if (distribution != JavaDistribution.GRAALVM_COMMUNITY && distribution != JavaDistribution.TEMURIN) {
            return Optional.empty();
        }
        String id = id(distribution, request);
        return Optional.of(new LockedJavaToolchain(
                id,
                request,
                platform,
                "21",
                distribution,
                "builtin:" + id,
                layout(distribution, request, platform)));
    }

    @Override
    public List<LockedJavaToolchain> locks(JavaToolchainRequest request, HostPlatform platform) {
        return SUPPORTED_PLATFORMS.stream()
                .map(target -> lock(request, target))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<JavaToolchainArtifact> artifact(LockedJavaToolchain locked) {
        return switch (locked.resolvedDistribution()) {
            case TEMURIN -> Optional.of(new JavaToolchainArtifact(
                    URI.create(temurinUrl(locked)),
                    archiveFormat(locked.platform()),
                    Optional.empty(),
                    true));
            case GRAALVM_COMMUNITY -> Optional.of(new JavaToolchainArtifact(
                    URI.create(graalVmCommunityUrl(locked)),
                    archiveFormat(locked.platform()),
                    Optional.empty(),
                    true));
            default -> Optional.empty();
        };
    }

    private static String id(JavaDistribution distribution, JavaToolchainRequest request) {
        return "java-" + distribution.id() + "-" + request.version()
                + (request.requiresNativeImage() ? "-native-image" : "");
    }

    private static JavaToolchainLayout layout(
            JavaDistribution distribution,
            JavaToolchainRequest request,
            HostPlatform platform) {
        String javaHome = platform.os() == OperatingSystem.MACOS ? "Contents/Home" : ".";
        String nativeImage = "";
        if (request.features().contains(JavaFeature.NATIVE_IMAGE)) {
            nativeImage = distribution == JavaDistribution.GRAALVM_COMMUNITY
                    ? "lib/svm/bin/native-image"
                    : "bin/native-image";
        }
        return new JavaToolchainLayout(
                javaHome,
                "bin/java",
                "bin/javac",
                "bin/jar",
                nativeImage);
    }

    private static String temurinUrl(LockedJavaToolchain locked) {
        return "https://api.adoptium.net/v3/binary/latest/"
                + locked.request().version()
                + "/ga/"
                + adoptiumOs(locked.platform().os())
                + "/"
                + adoptiumArch(locked.platform().arch())
                + "/jdk/hotspot/normal/eclipse";
    }

    private static String graalVmCommunityUrl(LockedJavaToolchain locked) {
        String extension = locked.platform().os() == OperatingSystem.WINDOWS ? "zip" : "tar.gz";
        return "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-"
                + GRAALVM_JDK_21
                + "/graalvm-community-jdk-"
                + GRAALVM_JDK_21
                + "_"
                + graalPlatform(locked.platform())
                + "_bin."
                + extension;
    }

    private static JavaToolchainArchiveFormat archiveFormat(HostPlatform platform) {
        return platform.os() == OperatingSystem.WINDOWS
                ? JavaToolchainArchiveFormat.ZIP
                : JavaToolchainArchiveFormat.TAR_GZ;
    }

    private static String adoptiumOs(OperatingSystem os) {
        return switch (os) {
            case LINUX -> "linux";
            case MACOS -> "mac";
            case WINDOWS -> "windows";
        };
    }

    private static String adoptiumArch(Architecture arch) {
        return switch (arch) {
            case X64 -> "x64";
            case AARCH64 -> "aarch64";
        };
    }

    private static String graalPlatform(HostPlatform platform) {
        return switch (platform.os()) {
            case LINUX -> platform.arch() == Architecture.X64 ? "linux-x64" : "linux-aarch64";
            case MACOS -> platform.arch() == Architecture.X64 ? "macos-x64" : "macos-aarch64";
            case WINDOWS -> "windows-x64";
        };
    }
}
