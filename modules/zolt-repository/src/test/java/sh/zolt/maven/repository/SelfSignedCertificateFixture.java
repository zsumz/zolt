package sh.zolt.maven.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Generates a throwaway self-signed certificate (PKCS12 keystore plus PEM certificate) at test time
 * with the JDK {@code keytool}, so custom-CA trust can be exercised without committing key material.
 * Returns empty when {@code keytool} is unavailable so the caller can skip.
 */
final class SelfSignedCertificateFixture {
    static final char[] PASSWORD = "changeit".toCharArray();

    private final Path keyStore;
    private final Path pemCertificate;

    private SelfSignedCertificateFixture(Path keyStore, Path pemCertificate) {
        this.keyStore = keyStore;
        this.pemCertificate = pemCertificate;
    }

    Path keyStore() {
        return keyStore;
    }

    Path pemCertificate() {
        return pemCertificate;
    }

    static Optional<SelfSignedCertificateFixture> generate(Path directory) {
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        if (!Files.isExecutable(keytool)) {
            return Optional.empty();
        }
        Path keyStore = directory.resolve("server.p12");
        Path pem = directory.resolve("ca.pem");
        boolean generated = run(List.of(
                keytool.toString(),
                "-genkeypair",
                "-alias", "zolt-selfsigned",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "3650",
                "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12",
                "-keystore", keyStore.toString(),
                "-storepass", "changeit",
                "-keypass", "changeit"));
        boolean exported = generated && run(List.of(
                keytool.toString(),
                "-exportcert",
                "-rfc",
                "-alias", "zolt-selfsigned",
                "-keystore", keyStore.toString(),
                "-storepass", "changeit",
                "-file", pem.toString()));
        if (!exported) {
            return Optional.empty();
        }
        return Optional.of(new SelfSignedCertificateFixture(keyStore, pem));
    }

    private static boolean run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
