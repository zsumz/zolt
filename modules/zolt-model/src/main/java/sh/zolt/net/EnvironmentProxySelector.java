package sh.zolt.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * A {@link ProxySelector} backed by a {@link ProxyConfiguration}. Java's built-in HTTP client does
 * not read {@code HTTPS_PROXY}/{@code HTTP_PROXY}/{@code NO_PROXY} environment variables, so this
 * selector bridges them (plus the conventional Java proxy system properties) into the client.
 */
final class EnvironmentProxySelector extends ProxySelector {
    private final ProxyConfiguration configuration;

    EnvironmentProxySelector(ProxyConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public List<Proxy> select(URI uri) {
        return configuration.proxyFor(uri)
                .map(address -> List.of(new Proxy(Proxy.Type.HTTP, (SocketAddress) address)))
                .orElse(List.of(Proxy.NO_PROXY));
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException exception) {
        // No failover list is maintained; the caller surfaces the connection failure.
    }
}
