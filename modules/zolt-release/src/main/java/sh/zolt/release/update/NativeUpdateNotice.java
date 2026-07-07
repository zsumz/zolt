package sh.zolt.release.update;

import sh.zolt.release.ReleaseTarget;
public record NativeUpdateNotice(
        String channel,
        ReleaseTarget target,
        String currentVersion,
        String availableVersion,
        boolean cached) {
    public String message() {
        return "A newer Zolt is available on "
                + channel
                + ": "
                + currentVersion
                + " -> "
                + availableVersion
                + ". Run `zolt self update` to download and verify it.";
    }
}
