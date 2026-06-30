package com.zolt.lockfile.toml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class ZoltLockfileReaderTestSupport {
    private ZoltLockfileReaderTestSupport() {}

    static String golden() throws IOException {
        return new String(
                ZoltLockfileReaderTestSupport.class.getResourceAsStream("/golden/zolt-lock-writer.golden").readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
