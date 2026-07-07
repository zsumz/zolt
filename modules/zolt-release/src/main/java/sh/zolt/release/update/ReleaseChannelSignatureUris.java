package sh.zolt.release.update;

import java.net.URI;

final class ReleaseChannelSignatureUris {
    private ReleaseChannelSignatureUris() {
    }

    static URI sidecar(URI channelUri) {
        String value = channelUri.toString();
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        int insertAt = value.length();
        if (query >= 0) {
            insertAt = query;
        }
        if (fragment >= 0) {
            insertAt = Math.min(insertAt, fragment);
        }
        return URI.create(value.substring(0, insertAt) + ".sig" + value.substring(insertAt));
    }
}
