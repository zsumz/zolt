package sh.zolt.net;

import sh.zolt.error.ActionableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Builds trust material whose anchors are the union of the JDK default trust store and one or more
 * PEM CA bundles. Augmenting rather than replacing the defaults keeps normal public TLS working
 * alongside a corporate interception root.
 */
public final class CaBundle {
    private CaBundle() {
    }

    /** An {@link SSLContext} that trusts the JDK defaults plus the supplied PEM CA bundles. */
    public static SSLContext augmentedSslContext(List<Path> pemBundles) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {augmentedTrustManager(pemBundles)}, null);
            return sslContext;
        } catch (GeneralSecurityException exception) {
            throw new ActionableException(
                    "Could not build a TLS context from the configured CA bundle: " + exception.getMessage(),
                    "Point ZOLT_CA_BUNDLE or [network].caBundle at a readable PEM file of one or more CA certificates.");
        }
    }

    /** An {@link X509TrustManager} trusting the JDK defaults plus the supplied PEM CA bundles. */
    public static X509TrustManager augmentedTrustManager(List<Path> pemBundles) {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            int index = 0;
            for (X509Certificate anchor : defaultTrustAnchors()) {
                trustStore.setCertificateEntry("jdk-default-" + index++, anchor);
            }
            for (Path bundle : pemBundles) {
                for (Certificate certificate : readCertificates(bundle)) {
                    trustStore.setCertificateEntry("zolt-ca-" + index++, certificate);
                }
            }
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager x509) {
                    return x509;
                }
            }
            throw new ActionableException(
                    "The platform did not provide an X.509 trust manager for the configured CA bundle.",
                    "Retry on a standard JDK, or unset ZOLT_CA_BUNDLE and [network].caBundle.");
        } catch (GeneralSecurityException | IOException exception) {
            throw new ActionableException(
                    "Could not build a TLS trust store from the configured CA bundle: " + exception.getMessage(),
                    "Point ZOLT_CA_BUNDLE or [network].caBundle at a readable PEM file of one or more CA certificates.");
        }
    }

    private static X509Certificate[] defaultTrustAnchors() throws GeneralSecurityException {
        TrustManagerFactory defaults =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaults.init((KeyStore) null);
        for (TrustManager trustManager : defaults.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509) {
                return x509.getAcceptedIssuers();
            }
        }
        return new X509Certificate[0];
    }

    private static Collection<? extends Certificate> readCertificates(Path bundle) {
        if (!Files.isReadable(bundle)) {
            throw new ActionableException(
                    "Configured CA bundle `" + bundle + "` does not exist or is not readable.",
                    "Point ZOLT_CA_BUNDLE or [network].caBundle at a readable PEM certificate file.");
        }
        try (InputStream input = Files.newInputStream(bundle)) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            List<Certificate> certificates = new ArrayList<>(factory.generateCertificates(input));
            if (certificates.isEmpty()) {
                throw new ActionableException(
                        "CA bundle `" + bundle + "` contained no certificates.",
                        "Provide a PEM file with one or more `-----BEGIN CERTIFICATE-----` blocks.");
            }
            return certificates;
        } catch (GeneralSecurityException | IOException exception) {
            throw new ActionableException(
                    "Could not read CA bundle `" + bundle + "`: " + exception.getMessage(),
                    "Provide a PEM file with one or more `-----BEGIN CERTIFICATE-----` blocks.");
        }
    }
}
