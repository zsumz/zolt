package sh.zolt.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class ProxyAuthenticatorTest {
    private static final String CREDENTIALLED_PROXY = "http://alice:s3cr3t@proxy.example.com:3128";

    @Test
    void suppliesCredentialsForMatchingProxyOnProxyChallenge() throws Exception {
        ProxyAuthenticator authenticator = new ProxyAuthenticator(proxy(CREDENTIALLED_PROXY));

        PasswordAuthentication credentials = Authenticator.requestPasswordAuthentication(
                authenticator, "proxy.example.com", null, 3128, "http", "proxy", "Basic",
                URI.create("http://proxy.example.com:3128").toURL(), Authenticator.RequestorType.PROXY);

        assertEquals("alice", credentials.getUserName());
        assertEquals("s3cr3t", new String(credentials.getPassword()));
    }

    @Test
    void refusesServerChallengesSoProxyCredentialsNeverReachOrigins() throws Exception {
        ProxyAuthenticator authenticator = new ProxyAuthenticator(proxy(CREDENTIALLED_PROXY));

        PasswordAuthentication credentials = Authenticator.requestPasswordAuthentication(
                authenticator, "repo.example.com", null, 443, "https", "realm", "Basic",
                URI.create("https://repo.example.com/").toURL(), Authenticator.RequestorType.SERVER);

        assertNull(credentials);
    }

    @Test
    void ignoresProxiesWithoutConfiguredCredentials() throws Exception {
        ProxyAuthenticator authenticator = new ProxyAuthenticator(proxy("http://proxy.example.com:3128"));

        PasswordAuthentication credentials = Authenticator.requestPasswordAuthentication(
                authenticator, "proxy.example.com", null, 3128, "http", "proxy", "Basic",
                URI.create("http://proxy.example.com:3128").toURL(), Authenticator.RequestorType.PROXY);

        assertNull(credentials);
    }

    private static ProxyConfiguration proxy(String httpProxyUrl) {
        Function<String, String> environment = key -> "HTTP_PROXY".equals(key) ? httpProxyUrl : null;
        return ProxyConfiguration.fromEnvironment(environment, key -> null);
    }
}
