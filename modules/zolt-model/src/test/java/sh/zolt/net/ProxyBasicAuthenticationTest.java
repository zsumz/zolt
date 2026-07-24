package sh.zolt.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ProxyBasicAuthenticationTest {
    private static final String PROPERTY = ProxyBasicAuthentication.TUNNELING_DISABLED_SCHEMES_PROPERTY;

    @Test
    void removesOnlyBasicFromDisabledSchemes() {
        assertEquals("", ProxyBasicAuthentication.withBasicTunnelingAllowed("Basic"));
        assertEquals("", ProxyBasicAuthentication.withBasicTunnelingAllowed("basic"));
        assertEquals("Digest", ProxyBasicAuthentication.withBasicTunnelingAllowed("Basic,Digest"));
        assertEquals("Digest,NTLM", ProxyBasicAuthentication.withBasicTunnelingAllowed("Digest, Basic , NTLM"));
        assertEquals("Digest", ProxyBasicAuthentication.withBasicTunnelingAllowed("Digest"));
        assertEquals("", ProxyBasicAuthentication.withBasicTunnelingAllowed(""));
        assertEquals("", ProxyBasicAuthentication.withBasicTunnelingAllowed(null));
    }

    @Test
    void warnsOnlyWhenCredentialsArriveAfterAClientWasAlreadyBuilt() {
        assertFalse(ProxyBasicAuthentication.shouldWarnTunnelingUnavailable(true));
        assertTrue(ProxyBasicAuthentication.shouldWarnTunnelingUnavailable(false));
    }

    @Test
    void clearsBasicWhenTheFirstClientCarriesCredentials() {
        String saved = System.getProperty(PROPERTY);
        try {
            System.setProperty(PROPERTY, "Basic,Digest");
            ProxyBasicAuthentication.resetForTesting();

            ProxyBasicAuthentication.prepareForClientBuild(true);

            assertEquals("Digest", System.getProperty(PROPERTY));
        } finally {
            restore(saved);
            ProxyBasicAuthentication.resetForTesting();
        }
    }

    @Test
    void leavesTheTunnelingPropertyUntouchedWithoutCredentials() {
        String saved = System.getProperty(PROPERTY);
        try {
            System.setProperty(PROPERTY, "Basic");
            ProxyBasicAuthentication.resetForTesting();

            ProxyBasicAuthentication.prepareForClientBuild(false);

            assertEquals("Basic", System.getProperty(PROPERTY));
        } finally {
            restore(saved);
            ProxyBasicAuthentication.resetForTesting();
        }
    }

    private static void restore(String saved) {
        if (saved == null) {
            System.clearProperty(PROPERTY);
        } else {
            System.setProperty(PROPERTY, saved);
        }
    }
}
