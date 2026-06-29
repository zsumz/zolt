package com.zolt.build.fingerprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

record BuildFingerprintCachedFileHash(
        Path path,
        long size,
        long lastModifiedNanos,
        String hash) {
    static BuildFingerprintCachedFileHash read(Path path, String hash) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        BasicFileAttributes attributes = Files.readAttributes(normalized, BasicFileAttributes.class);
        return new BuildFingerprintCachedFileHash(
                normalized,
                attributes.size(),
                attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS),
                hash);
    }

    boolean isCurrent() {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return attributes.isRegularFile()
                    && attributes.size() == size
                    && attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS) == lastModifiedNanos;
        } catch (IOException exception) {
            return false;
        }
    }
}
