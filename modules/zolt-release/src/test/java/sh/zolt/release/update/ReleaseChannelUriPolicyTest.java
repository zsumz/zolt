package sh.zolt.release.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

final class ReleaseChannelUriPolicyTest {
    @Test
    void acceptsHttpsWithHostAndLocalFileWhenExplicitlyAllowed() {
        ReleaseChannelUriPolicy.validate(URI.create("https://dist.zolt.sh/channels/zap.json"), false);
        ReleaseChannelUriPolicy.validate(URI.create("file:///tmp/zolt-channel.json"), true);

        assertTrue(ReleaseChannelUriPolicy.isLocalFile(URI.create("file:///tmp/zolt-channel.json")));
        assertFalse(ReleaseChannelUriPolicy.isLocalFile(URI.create("https://dist.zolt.sh/channels/zap.json")));
    }

    @Test
    void rejectsMissingSchemeCredentialsAndNonHttpsSchemes() {
        assertInvalid("dist.zolt.sh/channels/zap.json", "must use HTTPS");
        assertInvalid("https://user:pass@dist.zolt.sh/channels/zap.json", "must not include URL credentials");
        assertInvalid("http://dist.zolt.sh/channels/zap.json", "must be an HTTPS URL with a host");
        assertInvalid("https:///channels/zap.json", "must be an HTTPS URL with a host");
    }

    @Test
    void rejectsLocalFileWhenDisallowedOrNotLocalPath() {
        NativeUpdateException disallowed = assertThrows(
                NativeUpdateException.class,
                () -> ReleaseChannelUriPolicy.validate(URI.create("file:///tmp/zolt-channel.json"), false));
        NativeUpdateException authority = assertThrows(
                NativeUpdateException.class,
                () -> ReleaseChannelUriPolicy.validate(URI.create("file://dist.zolt.sh/channel.json"), true));

        assertTrue(disallowed.getMessage().contains("may use file: only"));
        assertTrue(authority.getMessage().contains("without an authority"));
    }

    private static void assertInvalid(String uri, String message) {
        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> ReleaseChannelUriPolicy.validate(URI.create(uri), false));

        assertTrue(exception.getMessage().contains(message), exception.getMessage());
    }
}
