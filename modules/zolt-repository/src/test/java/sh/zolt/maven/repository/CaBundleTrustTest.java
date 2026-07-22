package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import sh.zolt.error.ActionableException;
import sh.zolt.net.CaBundle;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CaBundleTrustTest {
    @Test
    void augmentedTrustManagerAddsCustomCaWithoutDroppingJdkDefaults(@TempDir Path directory) throws Exception {
        Optional<SelfSignedCertificateFixture> fixture = SelfSignedCertificateFixture.generate(directory);
        assumeTrue(fixture.isPresent(), "keytool is required to generate the self-signed CA fixture");
        Path pem = fixture.orElseThrow().pemCertificate();

        X509TrustManager augmented = CaBundle.augmentedTrustManager(List.of(pem));

        int defaultCount = defaultAcceptedIssuers().length;
        assertTrue(defaultCount > 0, "JDK default trust store should not be empty");
        assertTrue(
                augmented.getAcceptedIssuers().length >= defaultCount + 1,
                "augmented trust must retain the JDK defaults and add the custom CA");
        assertTrue(
                Arrays.asList(augmented.getAcceptedIssuers()).contains(loadCertificate(pem)),
                "augmented trust must contain the custom CA certificate");
    }

    @Test
    void missingBundleFailsWithActionableError(@TempDir Path directory) {
        Path missing = directory.resolve("absent.pem");

        ActionableException exception = assertThrows(
                ActionableException.class,
                () -> CaBundle.augmentedTrustManager(List.of(missing)));

        assertTrue(exception.getMessage().contains("does not exist or is not readable"));
    }

    @Test
    void emptyBundleFailsWithActionableError(@TempDir Path directory) throws Exception {
        Path empty = directory.resolve("empty.pem");
        Files.writeString(empty, "not a certificate\n");

        assertThrows(ActionableException.class, () -> CaBundle.augmentedTrustManager(List.of(empty)));
    }

    private static X509Certificate[] defaultAcceptedIssuers() throws Exception {
        TrustManagerFactory factory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (TrustManager trustManager : factory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509) {
                return x509.getAcceptedIssuers();
            }
        }
        return new X509Certificate[0];
    }

    private static X509Certificate loadCertificate(Path pem) throws Exception {
        try (InputStream input = Files.newInputStream(pem)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }
}
