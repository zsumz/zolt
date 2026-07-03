package sh.zolt.build.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.test.runtime.TestRunException;
import sh.zolt.test.shard.TestShardSpec;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestProfileSettingsTest {
    @TempDir
    private Path projectDir;

    @Test
    void derivesProfileDirectoriesForWorkspaceSuitesAndShards() {
        TestProfileSettings settings = TestProfileSettings.fromCli(true, Path.of("target/profiles"), 3, "2s")
                .forWorkspaceMember("modules/zolt-build")
                .forSuite("fast suite!")
                .forShard("fast suite!", new TestShardSpec(2, 3));

        assertTrue(settings.enabled());
        assertEquals(Optional.of(Path.of("target/profiles")
                .resolve("modules/zolt-build")
                .resolve("shards")
                .resolve("fast_suite_")
                .resolve("shard-2-of-3")), settings.profileDirectory());
        assertEquals(3, settings.summaryLimit());
        assertEquals(2_000L, settings.minimumDurationMillis());
        assertEquals(Optional.of("fast suite!"), settings.suiteName());
        assertEquals(Optional.of("2/3"), settings.shard());
        assertEquals(Optional.of("modules/zolt-build"), settings.workspaceMember());
    }

    @Test
    void disabledSettingsStayInertAndCliSummaryControlsEnableProfiling() {
        TestProfileSettings disabled = TestProfileSettings.fromCli(false, null);

        assertEquals(TestProfileSettings.disabled(), disabled);
        assertTrue(disabled.absoluteProfileDirectory(projectDir).isEmpty());
        assertEquals(disabled, disabled.forSuite("fast"));
        assertEquals(disabled, disabled.forShard("fast", new TestShardSpec(1, 2)));
        assertEquals(disabled, disabled.forWorkspaceMember("modules/zolt-build"));

        TestProfileSettings summaryOnly = TestProfileSettings.fromCli(false, null, 5, "250ms");

        assertTrue(summaryOnly.enabled());
        assertEquals(5, summaryOnly.summaryLimit());
        assertEquals(250L, summaryOnly.minimumDurationMillis());
        assertEquals(
                Optional.of(projectDir.toAbsolutePath().normalize().resolve("target/test-profile")),
                summaryOnly.absoluteProfileDirectory(projectDir));
    }

    @Test
    void cleansBlankOptionalLabelsAndDefaultsBlankSuiteNames() {
        TestProfileSettings settings = new TestProfileSettings(
                true,
                null,
                1,
                60_000L,
                Optional.of(" "),
                Optional.empty(),
                Optional.of("\t"));

        assertTrue(settings.profileDirectory().isEmpty());
        assertTrue(settings.suiteName().isEmpty());
        assertTrue(settings.shard().isEmpty());
        assertTrue(settings.workspaceMember().isEmpty());

        TestProfileSettings shard = settings.forShard("", new TestShardSpec(1, 1));

        assertEquals(Optional.of("all"), shard.suiteName());
        assertEquals(Optional.of(Path.of("target/test-profile/shards/all/shard-1-of-1")), shard.profileDirectory());
    }

    @Test
    void validatesProfileControlsAndDurations() {
        assertEquals(60_000L, TestProfileSettings.fromCli(true, Path.of("profiles"), 1, "1m")
                .minimumDurationMillis());

        TestRunException constructorTop = assertThrows(
                TestRunException.class,
                () -> new TestProfileSettings(
                        true,
                        Optional.empty(),
                        0,
                        0L,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()));
        TestRunException constructorMin = assertThrows(
                TestRunException.class,
                () -> new TestProfileSettings(
                        true,
                        Optional.empty(),
                        1,
                        -1L,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()));
        TestRunException cliMin = assertThrows(
                TestRunException.class,
                () -> TestProfileSettings.fromCli(true, Path.of("profiles"), 1, "-1s"));

        assertEquals("--profile-top requires a positive integer.", constructorTop.getMessage());
        assertEquals("--profile-min requires a non-negative duration.", constructorMin.getMessage());
        assertTrue(cliMin.getMessage().contains("Invalid --profile-min `-1s`"));
    }
}
