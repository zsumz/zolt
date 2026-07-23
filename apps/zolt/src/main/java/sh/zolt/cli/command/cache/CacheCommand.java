package sh.zolt.cli.command.cache;

import sh.zolt.build.cache.BuildCachePruneResult;
import sh.zolt.build.cache.BuildCacheSettings;
import sh.zolt.build.cache.BuildCacheStatus;
import sh.zolt.build.cache.LocalBuildCache;
import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.cli.command.CommandBuildCache;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** {@code zolt cache}: inspect and prune the local build-output cache (a machine-local store). */
@Command(
        name = "cache",
        description = "Inspect and prune the build-output cache.",
        subcommands = {CacheCommand.StatusCommand.class, CacheCommand.PruneCommand.class})
public final class CacheCommand implements Runnable {
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    @Command(name = "status", description = "Show the build-output cache location, entry count, and size.")
    public static final class StatusCommand implements Runnable {
        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            CommandHumanOutput output = CommandHumanOutput.of(spec);
            BuildCacheSettings settings = CommandBuildCache.settings();
            if (!settings.enabled()) {
                output.context("build cache", "disabled");
                output.next("Enable it in ~/.zolt/config.toml under [buildCache] with `enabled = true`.");
                return;
            }
            BuildCacheStatus status = localCache(settings).status();
            output.context("build cache", "enabled");
            output.context("directory", status.directory().toString());
            output.context("entries", Integer.toString(status.entryCount()));
            output.context("size", CacheFormat.bytes(status.totalBytes()));
            output.context("max size", CacheFormat.bytes(status.maxSizeBytes()));
        }
    }

    @Command(name = "prune", description = "Evict least-recently-used entries down to the size cap.")
    public static final class PruneCommand implements Runnable {
        @Option(
                names = "--max-size-mb",
                description = "Prune down to this cap for this run (megabytes) instead of the configured cap.")
        private Long maxSizeMb;

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            CommandHumanOutput output = CommandHumanOutput.of(spec);
            BuildCacheSettings settings = CommandBuildCache.settings();
            if (!settings.enabled()) {
                output.context("build cache", "disabled");
                return;
            }
            long cap = maxSizeMb != null ? Math.max(0L, maxSizeMb) * 1024L * 1024L : settings.maxSizeBytes();
            BuildCachePruneResult result = localCache(settings).prune(cap);
            output.summary(
                    "Pruned " + result.removedEntries() + " build cache entries",
                    CacheFormat.bytes(result.freedBytes()) + " freed");
            output.context("remaining", CacheFormat.bytes(result.remainingBytes()));
        }
    }

    private static LocalBuildCache localCache(BuildCacheSettings settings) {
        return new LocalBuildCache(settings.directory(), settings.maxSizeBytes());
    }

    private static final class CacheFormat {
        private CacheFormat() {
        }

        static String bytes(long bytes) {
            if (bytes >= 1024L * 1024L) {
                return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            }
            if (bytes >= 1024L) {
                return String.format("%.1f KB", bytes / 1024.0);
            }
            return bytes + " B";
        }
    }
}
