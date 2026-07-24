package sh.zolt.publish;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Maven checksum digests over an artifact's bytes. Every digest is computed by STREAMING the file
 * through {@link MessageDigest} in bounded chunks rather than reading it whole, so a multi-MB archive
 * is never buffered on the heap of the publish hot path (staging and Central-bundle assembly).
 */
public final class PublishChecksum {
    /**
     * Maven-standard checksum sidecars plus SHA-256, in deterministic upload order. MD5 and SHA-1
     * are the sidecars every Maven repository expects; SHA-256 is added for stronger verification
     * and for Central bundle validation.
     */
    static final List<Algorithm> SIDECAR_ALGORITHMS = List.of(
            new Algorithm("md5", "MD5"),
            new Algorithm("sha1", "SHA-1"),
            new Algorithm("sha256", "SHA-256"));

    private static final int BUFFER_SIZE = 1 << 16;

    private PublishChecksum() {
    }

    /** Prefixed SHA-256 digest used for package-evidence comparison and dry-run display. */
    static String sha256(Path path) {
        return "sha256:" + hex(path, "SHA-256");
    }

    /** Bare lowercase hex digest of {@code path} under the named JCA algorithm, computed by streaming. */
    public static String hex(Path path, String algorithm) {
        MessageDigest digest = messageDigest(algorithm);
        streamInto(path, digest);
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Computes every Maven checksum sidecar for {@code path} in a single streamed pass, returning bare
     * lowercase hex digests in {@link #SIDECAR_ALGORITHMS} order. The file is read once, in bounded
     * chunks, and never held whole on the heap.
     */
    public static List<Sidecar> sidecars(Path path) {
        List<MessageDigest> digests = new ArrayList<>();
        for (Algorithm algorithm : SIDECAR_ALGORITHMS) {
            digests.add(messageDigest(algorithm.jcaName()));
        }
        streamInto(path, digests.toArray(MessageDigest[]::new));
        List<Sidecar> sidecars = new ArrayList<>();
        for (int index = 0; index < SIDECAR_ALGORITHMS.size(); index++) {
            sidecars.add(new Sidecar(
                    SIDECAR_ALGORITHMS.get(index).extension(),
                    HexFormat.of().formatHex(digests.get(index).digest())));
        }
        return List.copyOf(sidecars);
    }

    /** Streams {@code path} once, updating every digest in bounded chunks — no full-file buffer. */
    private static void streamInto(Path path, MessageDigest... digests) {
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(path)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                for (MessageDigest digest : digests) {
                    digest.update(buffer, 0, read);
                }
            }
        } catch (IOException exception) {
            throw new PublishException("Could not read package artifact at " + path + ".", exception);
        }
    }

    private static MessageDigest messageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new PublishException(
                    "Could not compute package artifact checksum because " + algorithm + " is unavailable.",
                    exception);
        }
    }

    record Algorithm(String extension, String jcaName) {
    }

    /** One checksum sidecar: the file extension it lands at and its bare lowercase hex digest. */
    public record Sidecar(String extension, String value) {
    }
}
