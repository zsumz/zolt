package com.zolt.release;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ReleaseVerificationService {
    private final ProcessRunner processRunner;

    public ReleaseVerificationService() {
        this(ReleaseVerificationService::runProcess);
    }

    ReleaseVerificationService(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    public ReleaseVerificationResult verify(
            List<Path> archives,
            Path workDirectory,
            String expectedVersion) {
        if (archives.isEmpty()) {
            throw new ReleaseVerificationException(
                    "Release verification needs at least one archive. Pass an archive path.");
        }
        try {
            Files.createDirectories(workDirectory);
        } catch (IOException exception) {
            throw new ReleaseVerificationException(
                    "Could not create release verification work directory " + workDirectory + ". Check that it is writable.",
                    exception);
        }
        List<ReleaseVerificationResult.VerifiedArchive> verified = new ArrayList<>();
        for (Path archive : archives) {
            Path normalizedArchive = archive.normalize();
            try {
                verified.add(verifyArchive(normalizedArchive, workDirectory, expectedVersion));
            } catch (IOException exception) {
                throw archiveFailure(
                        normalizedArchive,
                        "could not unpack or read archive. Check that the archive is readable and not corrupt.");
            }
        }
        return new ReleaseVerificationResult(verified);
    }

    private ReleaseVerificationResult.VerifiedArchive verifyArchive(
            Path archive,
            Path workDirectory,
            String expectedVersion) throws IOException {
        if (!Files.isRegularFile(archive)) {
            throw archiveFailure(archive, "archive does not exist. Pass a valid release archive path.");
        }
        ReleaseTarget target = targetFromArchiveName(archive.getFileName().toString());
        verifyChecksum(archive);
        String rootName = rootName(archive.getFileName().toString(), target);
        Path unpackDirectory = Files.createTempDirectory(workDirectory, rootName + "-");
        if (target.zip()) {
            unpackZip(archive, unpackDirectory);
        } else {
            unpackTarGz(archive, unpackDirectory);
        }
        Path binary = unpackDirectory.resolve(rootName).resolve("bin").resolve(target.binaryName());
        if (!Files.isRegularFile(binary)) {
            throw archiveFailure(archive, "expected binary at " + binary + " after unpacking.");
        }
        binary.toFile().setExecutable(true);
        verifyVersion(archive, binary, expectedVersion);
        verifyInit(archive, binary, unpackDirectory);
        return new ReleaseVerificationResult.VerifiedArchive(archive, unpackDirectory, binary);
    }

    private static ReleaseTarget targetFromArchiveName(String archiveName) {
        for (ReleaseTarget target : ReleaseTarget.values()) {
            if (archiveName.endsWith("-" + target.id() + target.archiveExtension())) {
                return target;
            }
        }
        throw new ReleaseVerificationException(
                "Could not infer release target from archive `" + archiveName
                        + "`. Expected suffixes for: " + ReleaseTarget.supportedTargets() + ".");
    }

    private static String rootName(String archiveName, ReleaseTarget target) {
        return archiveName.substring(0, archiveName.length() - target.archiveExtension().length());
    }

    private static void verifyChecksum(Path archive) throws IOException {
        Path checksumPath = archive.resolveSibling(archive.getFileName() + ".sha256");
        if (!Files.isRegularFile(checksumPath)) {
            throw archiveFailure(archive, "missing checksum sidecar " + checksumPath.getFileName() + ".");
        }
        String expected = Files.readString(checksumPath, StandardCharsets.UTF_8).trim().split("\\s+", 2)[0];
        String actual = sha256(archive);
        if (!actual.equals(expected)) {
            throw archiveFailure(archive, "SHA-256 mismatch. Expected " + expected + " but found " + actual + ".");
        }
    }

    private void verifyVersion(Path archive, Path binary, String expectedVersion) {
        ProcessResult result = processRunner.run(List.of(binary.toString(), "--version"), binary.getParent());
        if (result.exitCode() != 0) {
            throw archiveFailure(archive, "`zolt --version` failed with exit code "
                    + result.exitCode() + ". Output:\n" + result.output());
        }
        if (!result.output().trim().equals(expectedVersion)) {
            throw archiveFailure(archive, "`zolt --version` did not print only expected version "
                    + expectedVersion + ". Output:\n" + result.output());
        }
    }

    private void verifyInit(Path archive, Path binary, Path unpackDirectory) {
        Path initDirectory = unpackDirectory.resolve("smoke-work");
        try {
            Files.createDirectories(initDirectory);
        } catch (IOException exception) {
            throw archiveFailure(archive, "could not create smoke work directory " + initDirectory + ".");
        }
        ProcessResult result = processRunner.run(
                List.of(binary.toString(), "init", "--cwd", initDirectory.toString(), "smoke"),
                unpackDirectory);
        if (result.exitCode() != 0) {
            throw archiveFailure(archive, "`zolt init smoke` failed with exit code "
                    + result.exitCode() + ". Output:\n" + result.output());
        }
        if (!Files.isRegularFile(initDirectory.resolve("smoke/zolt.toml"))) {
            throw archiveFailure(archive, "`zolt init smoke` did not create smoke/zolt.toml.");
        }
    }

    private static void unpackZip(Path archive, Path destination) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                Path output = safeResolve(destination, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    try (OutputStream file = Files.newOutputStream(output)) {
                        zip.transferTo(file);
                    }
                }
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        }
    }

    private static void unpackTarGz(Path archive, Path destination) throws IOException {
        try (InputStream input = new GZIPInputStream(Files.newInputStream(archive))) {
            byte[] header = input.readNBytes(512);
            while (header.length == 512 && !allZero(header)) {
                String name = readNullTerminated(header, 0, 100);
                int mode = Integer.parseInt(readNullTerminated(header, 100, 8).trim(), 8);
                long size = Long.parseLong(readNullTerminated(header, 124, 12).trim(), 8);
                byte type = header[156];
                Path output = safeResolve(destination, name);
                if (type == '5') {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    try (OutputStream file = Files.newOutputStream(output)) {
                        copyExactly(input, file, size);
                    }
                    output.toFile().setExecutable((mode & 0100) != 0);
                    skipPadding(input, size);
                }
                header = input.readNBytes(512);
            }
        }
    }

    private static Path safeResolve(Path destination, String entryName) {
        Path output = destination.resolve(entryName).normalize();
        if (!output.startsWith(destination.normalize())) {
            throw new ReleaseVerificationException(
                    "Release archive contains unsafe entry path `" + entryName + "`.");
        }
        return output;
    }

    private static void copyExactly(InputStream input, OutputStream output, long size) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new ReleaseVerificationException("Release archive ended before file content was complete.");
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skipPadding(InputStream input, long size) throws IOException {
        long padding = (512 - (size % 512)) % 512;
        while (padding > 0) {
            long skipped = input.skip(padding);
            if (skipped <= 0) {
                if (input.read() < 0) {
                    throw new ReleaseVerificationException("Release archive ended before file padding was complete.");
                }
                skipped = 1;
            }
            padding -= skipped;
        }
    }

    private static boolean allZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static String readNullTerminated(byte[] bytes, int offset, int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static String sha256(Path archivePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(archivePath)) {
                byte[] buffer = new byte[8192];
                int read = input.read(buffer);
                while (read >= 0) {
                    digest.update(buffer, 0, read);
                    read = input.read(buffer);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new ReleaseVerificationException(
                    "Could not verify SHA-256 checksum. SHA-256 is missing from this JDK.",
                    exception);
        }
    }

    private static ReleaseVerificationException archiveFailure(Path archive, String message) {
        return new ReleaseVerificationException("Release archive verification failed for " + archive + ": " + message);
    }

    private static ProcessResult runProcess(List<String> command, Path directory) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(directory.toFile());
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            return new ProcessResult(process.waitFor(), output);
        } catch (IOException exception) {
            throw new ReleaseVerificationException(
                    "Could not run release verification command `"
                            + String.join(" ", command)
                            + "`. Check that the archive binary can be executed.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ReleaseVerificationException("Release verification was interrupted.", exception);
        }
    }

    @FunctionalInterface
    interface ProcessRunner {
        ProcessResult run(List<String> command, Path directory);
    }

    record ProcessResult(int exitCode, String output) {
    }
}
