package com.zolt.build;

import com.zolt.build.incremental.IncrementalCompileState;
import com.zolt.build.incremental.IncrementalCompileStateCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

final class BuildFingerprintFileHasher {
    private static final List<String> LOCAL_FINGERPRINT_FILES = List.of(
            ".zolt-build-main.fingerprint",
            ".zolt-build-test.fingerprint",
            ".zolt-build-main.fingerprint.state",
            ".zolt-build-test.fingerprint.state");

    String hashText(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    String classpathHash(
            Path path,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            Optional<String> abiHash = zoltOutputAbiHash(normalized);
            if (abiHash.isPresent()) {
                return "abi:" + abiHash.orElseThrow();
            }
        }
        return fileHash(normalized, cachedState, collectedState);
    }

    String fileHash(
            Path path,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            return directoryHash(normalized, cachedState, collectedState);
        }
        if (!Files.isRegularFile(normalized)) {
            return "missing";
        }
        if (cachedState != null) {
            return cachedState.hashIfCurrent(normalized)
                    .orElseThrow(BuildFingerprintStateMiss::new);
        }
        try {
            String hash = sha256(Files.readAllBytes(normalized));
            if (collectedState != null) {
                collectedState.put(normalized, BuildFingerprintCachedFileHash.read(normalized, hash));
            }
            return hash;
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not fingerprint file "
                            + normalized
                            + ". Check that it is readable.",
                    exception);
        }
    }

    String relative(Path projectRoot, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(projectRoot)) {
            return projectRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    private Optional<String> zoltOutputAbiHash(Path directory) {
        Optional<IncrementalCompileState> state = new IncrementalCompileStateCodec()
                .read(IncrementalCompileState.mainStatePath(directory));
        if (state.isEmpty() || !state.orElseThrow().outputDirectory().equals(directory)) {
            return Optional.empty();
        }
        StringBuilder content = new StringBuilder();
        for (IncrementalCompileState.ClassRecord classRecord : state.orElseThrow().classes()) {
            content.append(classRecord.binaryName())
                    .append('|')
                    .append(classRecord.abiHash())
                    .append('|')
                    .append(classRecord.packagePrivateAbiHash())
                    .append('\n');
        }
        return Optional.of(hashText(content.toString()));
    }

    private String directoryHash(
            Path directory,
            BuildFingerprintState cachedState,
            Map<Path, BuildFingerprintCachedFileHash> collectedState) {
        try (Stream<Path> paths = Files.walk(directory)) {
            StringBuilder content = new StringBuilder();
            paths.filter(Files::isRegularFile)
                    .map(Path::normalize)
                    .filter(path -> !LOCAL_FINGERPRINT_FILES.contains(path.getFileName().toString()))
                    .sorted()
                    .forEach(path -> content
                            .append(directory.relativize(path).toString().replace('\\', '/'))
                            .append('|')
                            .append(fileHash(path, cachedState, collectedState))
                            .append('\n'));
            return hashText(content.toString());
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not fingerprint directory "
                            + directory
                            + ". Check that it is readable.",
                    exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new BuildException("Could not compute build fingerprint because SHA-256 is unavailable.", exception);
        }
    }
}
