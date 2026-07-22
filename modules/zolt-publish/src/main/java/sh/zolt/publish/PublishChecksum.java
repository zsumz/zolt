package sh.zolt.publish;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

final class PublishChecksum {
    /**
     * Maven-standard checksum sidecars plus SHA-256, in deterministic upload order. MD5 and SHA-1
     * are the sidecars every Maven repository expects; SHA-256 is added for stronger verification
     * and for Central bundle validation.
     */
    static final List<Algorithm> SIDECAR_ALGORITHMS = List.of(
            new Algorithm("md5", "MD5"),
            new Algorithm("sha1", "SHA-1"),
            new Algorithm("sha256", "SHA-256"));

    private PublishChecksum() {
    }

    /** Prefixed SHA-256 digest used for package-evidence comparison and dry-run display. */
    static String sha256(Path path) {
        return "sha256:" + hex(path, "SHA-256");
    }

    /** Bare lowercase hex digest of {@code path} under the named JCA algorithm. */
    static String hex(Path path, String algorithm) {
        try {
            return digestHex(Files.readAllBytes(path), algorithm);
        } catch (IOException exception) {
            throw new PublishException("Could not read package artifact at " + path + ".", exception);
        }
    }

    /**
     * Computes every Maven checksum sidecar for {@code path} in a single read, returning bare
     * lowercase hex digests in {@link #SIDECAR_ALGORITHMS} order.
     */
    static List<Sidecar> sidecars(Path path) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new PublishException("Could not read package artifact at " + path + ".", exception);
        }
        List<Sidecar> sidecars = new ArrayList<>();
        for (Algorithm algorithm : SIDECAR_ALGORITHMS) {
            sidecars.add(new Sidecar(algorithm.extension(), digestHex(bytes, algorithm.jcaName())));
        }
        return List.copyOf(sidecars);
    }

    private static String digestHex(byte[] bytes, String algorithm) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new PublishException(
                    "Could not compute package artifact checksum because " + algorithm + " is unavailable.",
                    exception);
        }
    }

    record Algorithm(String extension, String jcaName) {
    }

    record Sidecar(String extension, String value) {
    }
}
