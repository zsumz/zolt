package sh.zolt.net;

import java.net.Authenticator;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.net.ssl.SSLContext;

/**
 * Shared transport configuration for Zolt's outbound HTTP(S): an optional proxy selector, an
 * optional proxy Basic authenticator, and an optional trust store augmented with corporate CA
 * certificates. One instance configures every outbound client (artifact downloads, metadata
 * discovery, publish uploads, the Java toolchain download, and the remote build cache) so proxy,
 * proxy-auth, and TLS policy stay consistent across the tool.
 */
public final class NetworkTransport {
    /** Environment variable naming a PEM CA bundle to trust in addition to the JDK defaults. */
    public static final String CA_BUNDLE_ENV = "ZOLT_CA_BUNDLE";

    private final Optional<ProxySelector> proxySelector;
    private final Optional<SSLContext> sslContext;
    private final Optional<Authenticator> proxyAuthenticator;

    public NetworkTransport(Optional<ProxySelector> proxySelector, Optional<SSLContext> sslContext) {
        this(proxySelector, sslContext, Optional.empty());
    }

    NetworkTransport(
            Optional<ProxySelector> proxySelector,
            Optional<SSLContext> sslContext,
            Optional<Authenticator> proxyAuthenticator) {
        this.proxySelector = Objects.requireNonNull(proxySelector, "proxySelector");
        this.sslContext = Objects.requireNonNull(sslContext, "sslContext");
        this.proxyAuthenticator = Objects.requireNonNull(proxyAuthenticator, "proxyAuthenticator");
    }

    /** A transport with no proxy and the JDK default trust store. */
    public static NetworkTransport direct() {
        return new NetworkTransport(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Resolves a transport from ambient process state: proxy settings from environment variables and
     * Java system properties, plus a CA bundle named by {@code ZOLT_CA_BUNDLE}.
     */
    public static NetworkTransport fromEnvironment() {
        ProxyConfiguration proxy = ProxyConfiguration.fromEnvironment(System::getenv, System::getProperty);
        String caBundle = System.getenv(CA_BUNDLE_ENV);
        List<Path> caBundles = (caBundle == null || caBundle.isBlank())
                ? List.of()
                : List.of(Path.of(caBundle.trim()));
        return create(proxy, caBundles);
    }

    /**
     * Builds a transport from an explicit proxy configuration and CA bundle list. The caller is
     * responsible for applying any environment-over-config precedence before constructing the list.
     */
    public static NetworkTransport create(ProxyConfiguration proxy, List<Path> caBundles) {
        Optional<ProxySelector> selector = proxy.hasProxy()
                ? Optional.of(new EnvironmentProxySelector(proxy))
                : Optional.empty();
        Optional<SSLContext> sslContext = caBundles.isEmpty()
                ? Optional.empty()
                : Optional.of(CaBundle.augmentedSslContext(caBundles));
        Optional<Authenticator> proxyAuthenticator = proxy.hasProxyCredentials()
                ? Optional.of(new ProxyAuthenticator(proxy))
                : Optional.empty();
        return new NetworkTransport(selector, sslContext, proxyAuthenticator);
    }

    public HttpClient.Builder httpClientBuilder() {
        // Only when proxy credentials are configured, allow Basic auth for HTTPS CONNECT tunnels; this
        // must run before the first client is built (the JDK caches the property), so do it here.
        ProxyBasicAuthentication.prepareForClientBuild(proxyAuthenticator.isPresent());
        HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL);
        proxySelector.ifPresent(builder::proxy);
        sslContext.ifPresent(builder::sslContext);
        proxyAuthenticator.ifPresent(builder::authenticator);
        return builder;
    }

    public HttpClient newHttpClient() {
        return httpClientBuilder().build();
    }
}
