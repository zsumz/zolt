package sh.zolt.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class ProxyConfigurationTest {
    private static final Function<String, String> NO_PROPERTIES = key -> null;

    @Test
    void parsesHttpsProxyUrlWithSchemeAndPort() {
        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(
                env("HTTPS_PROXY", "http://proxy.example.com:8080"), NO_PROPERTIES);

        assertEquals(
                Optional.of(InetSocketAddress.createUnresolved("proxy.example.com", 8080)),
                config.proxyFor(URI.create("https://repo.maven.apache.org/maven2/")));
    }

    @Test
    void parsesBareHostPortWithoutScheme() {
        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(
                env("HTTP_PROXY", "proxy.example.com:3128"), NO_PROPERTIES);

        assertEquals(
                Optional.of(InetSocketAddress.createUnresolved("proxy.example.com", 3128)),
                config.proxyFor(URI.create("http://repo.example.com/a")));
    }

    @Test
    void lowercaseEnvironmentNameWinsOverUppercase() {
        Map<String, String> env = new HashMap<>();
        env.put("https_proxy", "http://lower.example.com:1000");
        env.put("HTTPS_PROXY", "http://upper.example.com:2000");

        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(env::get, NO_PROPERTIES);

        assertEquals(
                Optional.of(InetSocketAddress.createUnresolved("lower.example.com", 1000)),
                config.proxyFor(URI.create("https://repo.example.com/a")));
    }

    @Test
    void environmentTakesPrecedenceOverSystemProperty() {
        Function<String, String> properties = properties(Map.of(
                "https.proxyHost", "prop.example.com",
                "https.proxyPort", "9000"));

        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(
                env("HTTPS_PROXY", "http://env.example.com:8080"), properties);

        assertEquals(
                Optional.of(InetSocketAddress.createUnresolved("env.example.com", 8080)),
                config.proxyFor(URI.create("https://repo.example.com/a")));
    }

    @Test
    void systemPropertyIsUsedWhenEnvironmentIsAbsent() {
        Function<String, String> properties = properties(Map.of(
                "https.proxyHost", "prop.example.com",
                "https.proxyPort", "9000"));

        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(key -> null, properties);

        assertEquals(
                Optional.of(InetSocketAddress.createUnresolved("prop.example.com", 9000)),
                config.proxyFor(URI.create("https://repo.example.com/a")));
    }

    @Test
    void schemeSelectsMatchingProxy() {
        Map<String, String> env = new HashMap<>();
        env.put("HTTP_PROXY", "http://http-proxy.example.com:80");
        env.put("HTTPS_PROXY", "http://https-proxy.example.com:443");

        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(env::get, NO_PROPERTIES);

        assertEquals(
                Optional.of(InetSocketAddress.createUnresolved("http-proxy.example.com", 80)),
                config.proxyFor(URI.create("http://repo.example.com/a")));
        assertEquals(
                Optional.of(InetSocketAddress.createUnresolved("https-proxy.example.com", 443)),
                config.proxyFor(URI.create("https://repo.example.com/a")));
    }

    @Test
    void noProxyHostBypassesTheProxy() {
        Map<String, String> env = new HashMap<>();
        env.put("HTTPS_PROXY", "http://proxy.example.com:8080");
        env.put("NO_PROXY", ".internal.example.com");

        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(env::get, NO_PROPERTIES);

        assertTrue(config.proxyFor(URI.create("https://nexus.internal.example.com/a")).isEmpty());
        assertFalse(config.proxyFor(URI.create("https://repo.maven.apache.org/a")).isEmpty());
    }

    @Test
    void nonProxyHostsSystemPropertyIsTheNoProxyFallback() {
        Map<String, String> env = new HashMap<>();
        env.put("HTTPS_PROXY", "http://proxy.example.com:8080");
        Function<String, String> properties = properties(Map.of("http.nonProxyHosts", "*.internal.example.com"));

        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(env::get, properties);

        assertTrue(config.proxyFor(URI.create("https://svc.internal.example.com/a")).isEmpty());
    }

    @Test
    void noProxyConfiguredWhenNothingIsSet() {
        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(key -> null, NO_PROPERTIES);

        assertFalse(config.hasProxy());
        assertTrue(config.proxyFor(URI.create("https://repo.example.com/a")).isEmpty());
    }

    @Test
    void parsesBasicCredentialsFromProxyUrlUserInfo() {
        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(
                env("HTTP_PROXY", "http://alice:s3cr3t@proxy.example.com:3128"), NO_PROPERTIES);

        assertTrue(config.hasProxyCredentials());
        PasswordAuthentication credentials = config.credentialsFor("proxy.example.com", 3128).orElseThrow();
        assertEquals("alice", credentials.getUserName());
        assertEquals("s3cr3t", new String(credentials.getPassword()));
    }

    @Test
    void decodesPercentEncodedProxyPassword() {
        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(
                env("HTTPS_PROXY", "http://bob:p%40ss%3Aword@proxy.example.com:8080"), NO_PROPERTIES);

        PasswordAuthentication credentials = config.credentialsFor("proxy.example.com", 8080).orElseThrow();
        assertEquals("bob", credentials.getUserName());
        assertEquals("p@ss:word", new String(credentials.getPassword()));
    }

    @Test
    void hasNoCredentialsWhenUserInfoAbsent() {
        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(
                env("HTTP_PROXY", "http://proxy.example.com:3128"), NO_PROPERTIES);

        assertFalse(config.hasProxyCredentials());
        assertTrue(config.credentialsFor("proxy.example.com", 3128).isEmpty());
    }

    @Test
    void readsProxyCredentialsFromSystemProperties() {
        Function<String, String> properties = properties(Map.of(
                "https.proxyHost", "prop.example.com",
                "https.proxyPort", "9000",
                "https.proxyUser", "carol",
                "https.proxyPassword", "hunter2"));

        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(key -> null, properties);

        PasswordAuthentication credentials = config.credentialsFor("prop.example.com", 9000).orElseThrow();
        assertEquals("carol", credentials.getUserName());
        assertEquals("hunter2", new String(credentials.getPassword()));
    }

    @Test
    void scopesCredentialsToTheMatchingProxyEndpoint() {
        ProxyConfiguration config = ProxyConfiguration.fromEnvironment(
                env("HTTP_PROXY", "http://alice:s3cr3t@proxy.example.com:3128"), NO_PROPERTIES);

        assertTrue(config.credentialsFor("other.example.com", 3128).isEmpty());
        assertTrue(config.credentialsFor("proxy.example.com", 9999).isEmpty());
    }

    private static Function<String, String> env(String name, String value) {
        return key -> key.equals(name) ? value : null;
    }

    private static Function<String, String> properties(Map<String, String> values) {
        return values::get;
    }
}
