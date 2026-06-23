package com.zolt.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

final class BuildFingerprintStateStore {
    private static final String STATE_SUFFIX = ".state";

    Path fingerprintPath(Path outputDirectory, String fileName) {
        return outputDirectory.resolve(fileName);
    }

    Optional<BuildFingerprintState> readState(Path fingerprintPath) {
        Path statePath = statePath(fingerprintPath);
        if (!Files.isRegularFile(statePath)) {
            return Optional.empty();
        }
        try {
            return BuildFingerprintState.parse(Files.readAllLines(statePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not read build fingerprint state at "
                            + statePath
                            + ". Delete the file or rerun `zolt build` to refresh it.",
                    exception);
        }
    }

    void writeState(
            Path fingerprintPath,
            String fingerprint,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        Path statePath = statePath(fingerprintPath);
        try {
            Files.writeString(
                    statePath,
                    BuildFingerprintState.format(fingerprint, collectedState),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not write build fingerprint state at "
                            + statePath
                            + ". Check that the build output directory is writable.",
                    exception);
        }
    }

    private static Path statePath(Path fingerprintPath) {
        return fingerprintPath.resolveSibling(fingerprintPath.getFileName() + STATE_SUFFIX);
    }
}
