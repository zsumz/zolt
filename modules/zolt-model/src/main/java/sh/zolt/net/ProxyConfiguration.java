package sh.zolt.net;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolved HTTP/HTTPS proxy selection derived from environment variables and Java system
 * properties.
 *
 * <p>Precedence is per setting: the environment variable is consulted first and the conventional
 * Java system property is the fallback. That is, {@code https_proxy}/{@code HTTPS_PROXY} beats
 * {@code https.proxyHost}/{@code https.proxyPort}; {@code http_proxy}/{@code HTTP_PROXY} beats
 * {@code http.proxyHost}/{@code http.proxyPort}; and {@code no_proxy}/{@code NO_PROXY} beats
 * {@code http.nonProxyHosts}. Lowercase environment names win over uppercase ones.
 */
public final class ProxyConfiguration {
    private final Optional<InetSocketAddress> httpProxy;
    private final Optional<InetSocketAddress> httpsProxy;
    private final NoProxyRules noProxy;

    ProxyConfiguration(
            Optional<InetSocketAddress> httpProxy,
            Optional<InetSocketAddress> httpsProxy,
            NoProxyRules noProxy) {
        this.httpProxy = httpProxy;
        this.httpsProxy = httpsProxy;
        this.noProxy = noProxy;
    }

    public static ProxyConfiguration fromEnvironment(
            Function<String, String> environment,
            Function<String, String> systemProperties) {
        Optional<InetSocketAddress> https = proxyEndpoint(
                firstNonBlank(environment.apply("https_proxy"), environment.apply("HTTPS_PROXY")),
                systemProperties.apply("https.proxyHost"),
                systemProperties.apply("https.proxyPort"),
                443);
        Optional<InetSocketAddress> http = proxyEndpoint(
                firstNonBlank(environment.apply("http_proxy"), environment.apply("HTTP_PROXY")),
                systemProperties.apply("http.proxyHost"),
                systemProperties.apply("http.proxyPort"),
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

    Optional<InetSocketAddress> proxyFor(URI uri) {
        if (uri == null || uri.getScheme() == null) {
            return Optional.empty();
        }
        Optional<InetSocketAddress> candidate = switch (uri.getScheme().toLowerCase(Locale.ROOT)) {
            case "https" -> httpsProxy;
            case "http" -> httpProxy;
            default -> Optional.empty();
        };
        if (candidate.isEmpty() || noProxy.matches(uri.getHost())) {
            return Optional.empty();
        }
        return candidate;
    }

    private static Optional<InetSocketAddress> proxyEndpoint(
            String environmentValue,
            String propertyHost,
            String propertyPort,
            int defaultPort) {
        if (environmentValue != null && !environmentValue.isBlank()) {
            return parseProxyUrl(environmentValue, defaultPort);
        }
        if (propertyHost != null && !propertyHost.isBlank()) {
            return Optional.of(InetSocketAddress.createUnresolved(
                    propertyHost.trim(),
                    parsePort(propertyPort, defaultPort)));
        }
        return Optional.empty();
    }

    private static Optional<InetSocketAddress> parseProxyUrl(String value, int defaultPort) {
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
            return Optional.of(InetSocketAddress.createUnresolved(host, port));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
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
}
