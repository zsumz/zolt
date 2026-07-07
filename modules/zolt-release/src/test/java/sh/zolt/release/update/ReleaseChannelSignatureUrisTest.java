package sh.zolt.release.update;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

final class ReleaseChannelSignatureUrisTest {
    @Test
    void placesSignatureSuffixBeforeQueryAndFragment() {
        assertEquals(
                URI.create("https://dist.zolt.sh/channels/zap.json.sig"),
                ReleaseChannelSignatureUris.sidecar(URI.create("https://dist.zolt.sh/channels/zap.json")));
        assertEquals(
                URI.create("https://dist.zolt.sh/channels/zap.json.sig?cache=off"),
                ReleaseChannelSignatureUris.sidecar(URI.create("https://dist.zolt.sh/channels/zap.json?cache=off")));
        assertEquals(
                URI.create("https://dist.zolt.sh/channels/zap.json.sig#meta"),
                ReleaseChannelSignatureUris.sidecar(URI.create("https://dist.zolt.sh/channels/zap.json#meta")));
    }
}
