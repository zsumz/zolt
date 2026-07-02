package sh.zolt.release.update;

import java.net.URI;

final class ReleaseChannelUriPolicy {
    private ReleaseChannelUriPolicy() {
    }

    static void validate(URI uri, boolean allowLocalFile) {
        String scheme = uri.getScheme();
        if (scheme == null || scheme.isBlank()) {
            throw invalid("must use HTTPS.");
        }
        if (uri.getUserInfo() != null) {
            throw invalid("must not include URL credentials.");
        }
        if ("file".equalsIgnoreCase(scheme)) {
            validateLocalFile(uri, allowLocalFile);
            return;
        }
        if (!"https".equalsIgnoreCase(scheme) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid("must be an HTTPS URL with a host.");
        }
    }

    static boolean isLocalFile(URI uri) {
        return "file".equalsIgnoreCase(uri.getScheme());
    }

    private static void validateLocalFile(URI uri, boolean allowLocalFile) {
        if (!allowLocalFile) {
            throw invalid("may use file: only for explicit local development or test manifests.");
        }
        if (uri.getAuthority() != null && !uri.getAuthority().isBlank()) {
            throw invalid("file: manifests must be local paths without an authority.");
        }
        if (uri.getPath() == null || uri.getPath().isBlank()) {
            throw invalid("file: manifests must include a local path.");
        }
    }

    private static NativeUpdateException invalid(String detail) {
        return new NativeUpdateException("Release channel URL " + detail);
    }
}
