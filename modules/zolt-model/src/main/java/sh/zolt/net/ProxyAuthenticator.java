package sh.zolt.net;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Objects;

/**
 * Supplies HTTP Basic credentials for outbound proxies, and only for proxies. The JDK HTTP client
 * invokes this on a {@code 407 Proxy Authentication Required} challenge with the proxy endpoint;
 * server ({@code 401}) challenges deliberately return {@code null} so that proxy credentials are
 * never offered to an origin server and repository credentials are never offered to a proxy.
 */
final class ProxyAuthenticator extends Authenticator {
    private final ProxyConfiguration configuration;

    ProxyAuthenticator(ProxyConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        if (getRequestorType() != RequestorType.PROXY) {
            return null;
        }
        return configuration.credentialsFor(getRequestingHost(), getRequestingPort()).orElse(null);
    }
}
