package sh.zolt.net;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Enables HTTP Basic authentication for HTTPS-through-proxy {@code CONNECT} tunnels, but only when
 * proxy credentials are actually configured.
 *
 * <p>The JDK disables Basic for tunneled {@code CONNECT} by default via the
 * {@code jdk.http.auth.tunneling.disabledSchemes} system property, and it reads that property once —
 * the first time an {@link java.net.http.HttpClient} is built in the process. So we strip
 * {@code Basic} from that property before the first client is built, and only when credentials are
 * present, leaving the JDK's hardening in place for every other user.
 *
 * <p>There is a genuine JDK limitation we cannot engineer around: if some other client was already
 * built this process — so the property is already cached with {@code Basic} disabled — clearing it
 * afterward has no effect and HTTPS-through-proxy Basic authentication is unavailable for the run.
 * Plain HTTP-origin proxy authentication is unaffected and always works through the
 * {@link ProxyAuthenticator}. When we detect that late-arriving-credentials case we emit a single
 * stderr warning naming the remedy, rather than silently failing the tunnel.
 */
final class ProxyBasicAuthentication {
    static final String TUNNELING_DISABLED_SCHEMES_PROPERTY = "jdk.http.auth.tunneling.disabledSchemes";
    private static final String JDK_DEFAULT_DISABLED_SCHEMES = "Basic";

    private static final AtomicBoolean anyClientBuilt = new AtomicBoolean(false);
    private static final AtomicBoolean tunnelingWarningEmitted = new AtomicBoolean(false);

    private ProxyBasicAuthentication() {
    }

    /**
     * Called once per {@link java.net.http.HttpClient} build, before the client is constructed. When
     * proxy credentials are present, clears {@code Basic} from the tunneling disabled-schemes property
     * so CONNECT tunnels can authenticate; warns once if a client was already built and the property
     * is therefore already cached.
     */
    static void prepareForClientBuild(boolean hasProxyCredentials) {
        boolean firstClientInProcess = anyClientBuilt.compareAndSet(false, true);
        if (!hasProxyCredentials) {
            return;
        }
        String current = System.getProperty(TUNNELING_DISABLED_SCHEMES_PROPERTY, JDK_DEFAULT_DISABLED_SCHEMES);
        System.setProperty(TUNNELING_DISABLED_SCHEMES_PROPERTY, withBasicTunnelingAllowed(current));
        if (shouldWarnTunnelingUnavailable(firstClientInProcess)
                && tunnelingWarningEmitted.compareAndSet(false, true)) {
            System.err.println(
                    "zolt: warning: proxy credentials are set, but an HTTP client was already initialized this run, "
                            + "so Basic authentication over HTTPS-through-proxy (CONNECT tunnels) may be unavailable. "
                            + "Plain HTTP proxying still authenticates. If HTTPS artifact downloads fail with a 407, "
                            + "rerun the command or pass -Djdk.http.auth.tunneling.disabledSchemes= to the JVM.");
        }
    }

    /** Removes {@code Basic} (case-insensitively) from a comma-separated disabled-schemes value. */
    static String withBasicTunnelingAllowed(String disabledSchemes) {
        if (disabledSchemes == null || disabledSchemes.isBlank()) {
            return "";
        }
        return Arrays.stream(disabledSchemes.split(","))
                .map(String::trim)
                .filter(scheme -> !scheme.isEmpty())
                .filter(scheme -> !scheme.toLowerCase(Locale.ROOT).equals("basic"))
                .collect(Collectors.joining(","));
    }

    /**
     * Warn only when credentials arrived after some client already forced the JDK to cache the
     * restrictive default; when this is the first client, clearing the property is guaranteed to take.
     */
    static boolean shouldWarnTunnelingUnavailable(boolean firstClientInProcess) {
        return !firstClientInProcess;
    }

    // Visible for testing: reset the process-global latches.
    static void resetForTesting() {
        anyClientBuilt.set(false);
        tunnelingWarningEmitted.set(false);
    }
}
