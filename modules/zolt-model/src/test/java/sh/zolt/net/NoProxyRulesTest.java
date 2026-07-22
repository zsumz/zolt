package sh.zolt.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NoProxyRulesTest {
    @Test
    void exactHostMatches() {
        NoProxyRules rules = NoProxyRules.parse("internal.example.com");

        assertTrue(rules.matches("internal.example.com"));
        assertFalse(rules.matches("other.example.com"));
    }

    @Test
    void bareDomainMatchesApexAndSubdomains() {
        NoProxyRules rules = NoProxyRules.parse("example.com");

        assertTrue(rules.matches("example.com"));
        assertTrue(rules.matches("repo.example.com"));
        assertFalse(rules.matches("example.com.attacker.net"));
        assertFalse(rules.matches("notexample.com"));
    }

    @Test
    void leadingDotSuffixMatchesApexAndSubdomains() {
        NoProxyRules rules = NoProxyRules.parse(".example.com");

        assertTrue(rules.matches("example.com"));
        assertTrue(rules.matches("nexus.example.com"));
    }

    @Test
    void starBypassesEveryHost() {
        NoProxyRules rules = NoProxyRules.parse("*");

        assertTrue(rules.matches("anything.example.org"));
        assertTrue(rules.matches("127.0.0.1"));
    }

    @Test
    void wildcardPrefixIsTreatedAsSuffix() {
        NoProxyRules rules = NoProxyRules.parse("*.corp.example");

        assertTrue(rules.matches("host.corp.example"));
        assertTrue(rules.matches("corp.example"));
    }

    @Test
    void commaAndPipeSeparatorsAreBothSupported() {
        NoProxyRules rules = NoProxyRules.parse("localhost, .internal | 10.example.com");

        assertTrue(rules.matches("localhost"));
        assertTrue(rules.matches("svc.internal"));
        assertTrue(rules.matches("10.example.com"));
        assertFalse(rules.matches("public.example.org"));
    }

    @Test
    void portsAreStrippedFromEntriesButLoopbackIsPreserved() {
        NoProxyRules rules = NoProxyRules.parse("registry.example.com:8443, ::1");

        assertTrue(rules.matches("registry.example.com"));
        assertTrue(rules.matches("::1"));
    }

    @Test
    void blankSpecificationMatchesNothing() {
        NoProxyRules rules = NoProxyRules.parse("   ");

        assertFalse(rules.matches("example.com"));
        assertFalse(rules.matches("localhost"));
    }
}
