package sh.zolt.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class EnvironmentProxySelectorTest {
    @Test
    void selectReturnsConfiguredProxyForProxiedHost() {
        EnvironmentProxySelector selector = selector(Map.of("HTTPS_PROXY", "http://proxy.example.com:8080"));

        List<Proxy> proxies = selector.select(URI.create("https://repo.maven.apache.org/maven2/"));

        assertEquals(
                List.of(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080))),
                proxies);
    }

    @Test
    void selectReturnsNoProxyForBypassedHost() {
        EnvironmentProxySelector selector = selector(Map.of(
                "HTTPS_PROXY", "http://proxy.example.com:8080",
                "NO_PROXY", "repo.internal.example"));

        assertEquals(
                List.of(Proxy.NO_PROXY),
                selector.select(URI.create("https://repo.internal.example/maven2/")));
    }

    @Test
    void selectReturnsNoProxyWhenNoProxyConfigured() {
        EnvironmentProxySelector selector = selector(Map.of());

        assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create("https://repo.example.com/a")));
    }

    private static EnvironmentProxySelector selector(Map<String, String> environment) {
        return new EnvironmentProxySelector(ProxyConfiguration.fromEnvironment(environment::get, key -> null));
    }
}
