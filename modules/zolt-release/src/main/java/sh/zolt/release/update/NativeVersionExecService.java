package sh.zolt.release.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class NativeVersionExecService {
    private static final String BINARY_NAME = "zolt";

    public NativeVersionExecPlan plan(NativeVersionExecRequest request) {
        try {
            NativeInstalledLayout installed = NativeInstalledLayout.detect(request.installRoot(), request.currentExecutable());
            String version = safeVersion(request.version());
            List<String> arguments = normalizeArguments(request.arguments());
            Path versionsRoot = NativeInstallPaths.ownedDirectory(installed.versionsDirectory(), "native versions directory");
            Path installDirectory = NativeInstallPaths.resolveUnderRoot(versionsRoot, version, "native exec install directory");
            if (!Files.isDirectory(installDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw notInstalled(version, versionsRoot);
            }
            Path executable = installDirectory.resolve("bin").resolve(BINARY_NAME).normalize();
            NativeInstallPaths.assertUnderRoot(installDirectory, executable, "native exec binary");
            if (!Files.isExecutable(executable)) {
                throw notInstalled(version, versionsRoot);
            }
            return new NativeVersionExecPlan(version, executable, arguments);
        } catch (IOException exception) {
            throw new NativeUpdateException("Could not prepare native Zolt exec: " + exception.getMessage(), exception);
        }
    }

    private static NativeUpdateException notInstalled(String version, Path versionsRoot) {
        return new NativeUpdateException("Native Zolt version `" + version + "` is not installed under " + versionsRoot + ".");
    }

    private static List<String> normalizeArguments(List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            throw new NativeUpdateException("Native Zolt exec requires a command after `--`.");
        }
        List<String> normalized = List.copyOf(arguments);
        if (normalized.getFirst().equals("zolt")) {
            normalized = normalized.subList(1, normalized.size());
        }
        if (normalized.isEmpty()) {
            throw new NativeUpdateException("Native Zolt exec requires a command after `--`.");
        }
        return normalized;
    }

    private static String safeVersion(String version) {
        String normalized = Objects.requireNonNull(version, "version").strip();
        if (normalized.isEmpty()
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.contains("..")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new NativeUpdateException("Native Zolt version must be one installed version path segment.");
        }
        return normalized;
    }
}
