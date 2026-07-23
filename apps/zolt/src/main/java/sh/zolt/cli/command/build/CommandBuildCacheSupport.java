package sh.zolt.cli.command.build;

import sh.zolt.build.BuildService;
import sh.zolt.build.cache.BuildCacheService;
import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.cli.command.CommandBuildCache;
import sh.zolt.workspace.service.WorkspaceBuildService;

/**
 * The build-output cache wiring for {@code zolt build}: resolve the cache once from config (honoring
 * {@code --no-build-cache}/{@code --offline}), attach it to the build or workspace-build service, and
 * surface any deferred cache warnings after the run. Keeps {@link BuildCommand} from importing the cache
 * service and resolver directly.
 */
final class CommandBuildCacheSupport {
    private final BuildCacheService buildCache;

    private CommandBuildCacheSupport(BuildCacheService buildCache) {
        this.buildCache = buildCache;
    }

    static CommandBuildCacheSupport create(boolean disabledByFlag, boolean offline) {
        return new CommandBuildCacheSupport(CommandBuildCache.service(disabledByFlag, offline));
    }

    BuildService applyTo(BuildService buildService) {
        return buildService.withBuildCache(buildCache);
    }

    WorkspaceBuildService applyTo(WorkspaceBuildService workspaceBuildService) {
        return workspaceBuildService.withBuildCache(buildCache);
    }

    void surfaceWarnings(CommandHumanOutput output) {
        CommandBuildCache.surfaceWarnings(output, buildCache);
    }
}
