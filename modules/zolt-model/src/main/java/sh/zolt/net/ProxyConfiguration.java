package sh.zolt.net;

import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolved HTTP/HTTPS proxy selection derived from environment variables and Java system
 * properties, including any Basic credentials embedded in the proxy URL userinfo or supplied via
 * the conventional {@code http.proxyUser}/{@code http.proxyPassword} properties.
 *
 * <p>Precedence is per setting: the environment variable is consulted first and the conventional
 * Java system property is the fallback. That is, {@code https_proxy}/{@code HTTPS_PROXY} beats
 * {@code https.proxyHost}/{@code https.proxyPort}; {@code http_proxy}/{@code HTTP_PROXY} beats
 * {@code http.proxyHost}/{@code http.proxyPort}; and {@code no_proxy}/{@code NO_PROXY} beats
 * {@code http.nonProxyHosts}. Lowercase environment names win over uppercase ones. Credentials
 * follow the endpoint they were declared with: userinfo when the endpoint came from a proxy URL,
 * {@code *.proxyUser}/{@code *.proxyPassword} when it came from system properties.
 */
public final class ProxyConfiguration {
    private final Optional<ProxyEndpoint> httpProxy;
    private final Optional<ProxyEndpoint> httpsProxy;
    private final NoProxyRules noProxy;

    ProxyConfiguration(
            Optional<ProxyEndpoint> httpProxy,
            Optional<ProxyEndpoint> httpsProxy,
            NoProxyRules noProxy) {
        this.httpProxy = httpProxy;
        this.httpsProxy = httpsProxy;
        this.noProxy = noProxy;
    }

    public static ProxyConfiguration fromEnvironment(
            Function<String, String> environment,
            Function<String, String> systemProperties) {
        Optional<ProxyEndpoint> https = proxyEndpoint(
                firstNonBlank(environment.apply("https_proxy"), environment.apply("HTTPS_PROXY")),
                systemProperties.apply("https.proxyHost"),
                systemProperties.apply("https.proxyPort"),
                systemProperties.apply("https.proxyUser"),
                systemProperties.apply("https.proxyPassword"),
                443);
        Optional<ProxyEndpoint> http = proxyEndpoint(
                firstNonBlank(environment.apply("http_proxy"), environment.apply("HTTP_PROXY")),
                systemProperties.apply("http.proxyHost"),
                systemProperties.apply("http.proxyPort"),
                systemProperties.apply("http.proxyUser"),
                systemProperties.apply("http.proxyPassword"),
                80);
        String noProxySpecification = firstNonBlank(
                environment.apply("no_proxy"),
                environment.apply("NO_PROXY"),
                systemProperties.apply("http.nonProxyHosts"));
        return new ProxyConfiguration(http, https, NoProxyRules.parse(noProxySpecification));
    }

    public boolean hasProxy() {
        return httpProxy.isPresent() || httpsProxy.isPresent();
    }

    /** True when at least one configured proxy carries Basic credentials to authenticate with. */
    public boolean hasProxyCredentials() {
        return credentials(httpProxy).isPresent() || credentials(httpsProxy).isPresent();
    }

    Optional<InetSocketAddress> proxyFor(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            return Optional.empty();
        }
        Optional<ProxyEndpoint> candidate = switch (uri.getScheme().toLowerCase(Locale.ROOT)) {
            case "https" -> httpsProxy;
            case "http" -> httpProxy;
            default -> Optional.empty();
        };
        if (candidate.isEmpty() || noProxy.matches(uri.getHost())) {
            return Optional.empty();
        }
        return candidate.map(ProxyEndpoint::address);
    }

    /**
     * Basic credentials for the proxy reachable at {@code host:port}, if any were configured. Used by
     * the proxy {@link java.net.Authenticator}, which is handed the proxy endpoint (not the origin) on
     * a {@code 407 Proxy Authentication Required} challenge.
     */
    Optional<PasswordAuthentication> credentialsFor(String host, int port) {
        return matchingCredentials(httpsProxy, host, port)
                .or(() -> matchingCredentials(httpProxy, host, port));
    }

    private static Optional<PasswordAuthentication> matchingCredentials(
            Optional<ProxyEndpoint> endpoint, String host, int port) {
        return endpoint
                .filter(candidate -> candidate.address().getPort() == port
                        && candidate.address().getHostString().equalsIgnoreCase(host))
                .flatMap(ProxyEndpoint::credentials);
    }

    private static Optional<PasswordAuthentication> credentials(Optional<ProxyEndpoint> endpoint) {
        return endpoint.flatMap(ProxyEndpoint::credentials);
    }

    private static Optional<ProxyEndpoint> proxyEndpoint(
            String environmentValue,
            String propertyHost,
            String propertyPort,
            String propertyUser,
            String propertyPassword,
            int defaultPort) {
        if (environmentValue != null && !environmentValue.isBlank()) {
            return parseProxyUrl(environmentValue, defaultPort);
        }
        if (propertyHost != null && !propertyHost.isBlank()) {
            InetSocketAddress address = InetSocketAddress.createUnresolved(
                    propertyHost.trim(),
                    parsePort(propertyPort, defaultPort));
            return Optional.of(new ProxyEndpoint(address, systemPropertyCredentials(propertyUser, propertyPassword)));
        }
        return Optional.empty();
    }

    private static Optional<ProxyEndpoint> parseProxyUrl(String value, int defaultPort) {
        String candidate = value.trim();
        if (!candidate.contains("://")) {
            candidate = "http://" + candidate;
        }
        try {
            URI uri = URI.create(candidate);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return Optional.empty();
            }
            int schemeDefaultPort = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : defaultPort;
            int port = uri.getPort() == -1 ? schemeDefaultPort : uri.getPort();
            InetSocketAddress address = InetSocketAddress.createUnresolved(host, port);
            return Optional.of(new ProxyEndpoint(address, userInfoCredentials(uri.getUserInfo())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * Parses {@code user:password} from a proxy URL's userinfo. {@link URI#getUserInfo()} already
     * percent-decodes the value (and, unlike {@code URLDecoder}, leaves {@code +} untouched), so a
     * password with encoded specials such as {@code %40} round-trips correctly. The split is on the
     * first colon, so a password may itself contain colons.
     */
    private static Optional<PasswordAuthentication> userInfoCredentials(String userInfo) {
        if (userInfo == null || userInfo.isEmpty()) {
            return Optional.empty();
        }
        int separator = userInfo.indexOf(':');
        String username = separator < 0 ? userInfo : userInfo.substring(0, separator);
        String password = separator < 0 ? "" : userInfo.substring(separator + 1);
        if (username.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PasswordAuthentication(username, password.toCharArray()));
    }

    private static Optional<PasswordAuthentication> systemPropertyCredentials(String user, String password) {
        if (user == null || user.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new PasswordAuthentication(user.trim(), (password == null ? "" : password).toCharArray()));
    }

    private static int parsePort(String value, int defaultPort) {
        if (value == null || value.isBlank()) {
            return defaultPort;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return defaultPort;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** A resolved proxy endpoint and any Basic credentials parsed from its URL userinfo or properties. */
    record ProxyEndpoint(InetSocketAddress address, Optional<PasswordAuthentication> credentials) {
    }
}
