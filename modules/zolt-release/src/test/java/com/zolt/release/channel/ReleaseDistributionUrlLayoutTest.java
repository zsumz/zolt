package com.zolt.release.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.release.ReleaseTarget;
import org.junit.jupiter.api.Test;

final class ReleaseDistributionUrlLayoutTest {
    @Test
    void dryRunPublicationUrlsUseOwnedDomainForInstallerChannelsAndArchives() {
        ReleaseDistributionUrlLayout urls = new ReleaseDistributionUrlLayout();

        assertEquals("https://dist.zolt.sh/install.sh", urls.installScriptUrl());
        assertEquals("https://dist.zolt.sh/channels/stable.json", urls.channelManifestUrl("stable"));
        assertEquals("https://dist.zolt.sh/channels/nightly.json", urls.channelManifestUrl("nightly"));
        assertEquals("https://dist.zolt.sh/channels/zap.json", urls.channelManifestUrl("zap"));
        assertEquals(
                "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz",
                urls.archiveUrl("stable", "0.1.0", "zolt-0.1.0-linux-x64.tar.gz"));
        assertEquals(
                "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz.sha256",
                urls.checksumUrl("stable", "0.1.0", "zolt-0.1.0-linux-x64.tar.gz"));
        assertEquals(
                "https://dist.zolt.sh/artifacts/stable/0.1.0/zolt-0.1.0-linux-x64.tar.gz.minisig",
                urls.signatureUrl("stable", "0.1.0", "zolt-0.1.0-linux-x64.tar.gz", ".minisig"));
    }

    @Test
    void layoutCoversEveryNativeReleaseTargetWithoutJvmArtifacts() {
        ReleaseDistributionUrlLayout urls = new ReleaseDistributionUrlLayout();

        for (ReleaseTarget target : ReleaseTarget.values()) {
            String archive = "zolt-0.1.0-" + target.id() + target.archiveExtension();
            String archiveUrl = urls.archiveUrl("stable", "0.1.0", archive);

            assertTrue(archiveUrl.startsWith("https://dist.zolt.sh/artifacts/stable/0.1.0/"));
            assertTrue(archiveUrl.endsWith(target.archiveExtension()));
            assertTrue(!archiveUrl.contains(".jar"));
            assertTrue(!archiveUrl.contains("jre"));
            assertTrue(!archiveUrl.contains("jvm"));
        }
    }

    @Test
    void layoutRejectsNonHttpsAndUnsafePathSegments() {
        assertThrows(ReleaseChannelManifestException.class, () -> new ReleaseDistributionUrlLayout("http://dist.zolt.sh"));

        ReleaseDistributionUrlLayout urls = new ReleaseDistributionUrlLayout();
        assertThrows(ReleaseChannelManifestException.class, () -> urls.channelManifestUrl("../stable"));
        assertThrows(ReleaseChannelManifestException.class, () -> urls.archiveUrl("stable", "0.1.0", "../zolt.tar.gz"));
        assertThrows(ReleaseChannelManifestException.class, () -> urls.signatureUrl("stable", "0.1.0", "zolt.tar.gz", "/minisig"));
    }
}
