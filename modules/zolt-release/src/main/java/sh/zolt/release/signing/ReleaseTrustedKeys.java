package sh.zolt.release.signing;

import java.util.List;

public final class ReleaseTrustedKeys {
    public static final String ZOLT_RELEASE_2026_KEY_ID = "zolt-release-2026";
    private static final String ZOLT_RELEASE_2026_PUBLIC_KEY =
            "MCowBQYDK2VwAyEAn6cIrOCATTABSbWHl34vlZlP6xu/sFN8rxKga+/M/ZU=";

    private ReleaseTrustedKeys() {
    }

    public static List<ReleaseSigningKey> bundled() {
        return List.of(new ReleaseSigningKey(
                ZOLT_RELEASE_2026_KEY_ID,
                "Ed25519",
                ZOLT_RELEASE_2026_PUBLIC_KEY));
    }
}
