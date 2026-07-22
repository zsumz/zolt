package sh.zolt.publish;

/**
 * A Maven checksum sidecar that publish uploads alongside an artifact or POM. {@code subject}
 * names the file it verifies, {@code algorithm} is the sidecar extension ({@code md5}, {@code sha1},
 * {@code sha256}), {@code uploadPath} is the repository-relative destination, and {@code value} is
 * the bare lowercase hex digest.
 */
public record PublishChecksumSidecar(String subject, String algorithm, String uploadPath, String value) {
    public PublishChecksumSidecar {
        subject = subject == null ? "" : subject;
        algorithm = algorithm == null ? "" : algorithm;
        uploadPath = uploadPath == null ? "" : uploadPath;
        value = value == null ? "" : value;
    }
}
