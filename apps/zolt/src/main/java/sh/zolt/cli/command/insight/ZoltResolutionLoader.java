package sh.zolt.cli.command.insight;

import sh.zolt.error.ActionableException;
import sh.zolt.explain.verify.ResolvedModule;
import sh.zolt.explain.verify.ZoltModuleMapper;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.resolve.ResolveOptions;
import sh.zolt.resolve.ResolveService;
import sh.zolt.toml.ZoltTomlParser;
import sh.zolt.workspace.discovery.WorkspaceDiscoveryService;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceMember;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the Zolt side of the comparison: either a single project or every member of a workspace,
 * each via {@link ResolveService#resolveLockfile} (the same resolver {@code zolt resolve} uses), then
 * maps the resolved {@link LockPackage}s into per-module {@link ResolvedModule}s.
 *
 * <p>Workspace mode resolves each member from its own {@code zolt.toml}. Workspace-root shared
 * {@code [repositories]}/{@code [platforms]} are not folded into members in this first version; a
 * workspace that relies on them may need those inlined into members (an emitted draft can carry them)
 * for Zolt-side resolution here to match {@code zolt resolve --workspace}.
 */
final class ZoltResolutionLoader {

    /** Result of loading and resolving the Zolt side. */
    record ZoltResolution(List<ResolvedModule> modules, Map<String, String> memberPaths, String root) {
    }

    private final ResolveService resolveService;
    private final ZoltTomlParser tomlParser;
    private final WorkspaceDiscoveryService discoveryService;
    private final ZoltModuleMapper moduleMapper;

    ZoltResolutionLoader(ResolveService resolveService) {
        this(resolveService, new ZoltTomlParser(), new WorkspaceDiscoveryService(), new ZoltModuleMapper());
    }

    ZoltResolutionLoader(
            ResolveService resolveService,
            ZoltTomlParser tomlParser,
            WorkspaceDiscoveryService discoveryService,
            ZoltModuleMapper moduleMapper) {
        this.resolveService = resolveService;
        this.tomlParser = tomlParser;
        this.discoveryService = discoveryService;
        this.moduleMapper = moduleMapper;
    }

    ZoltResolution load(Path zoltDir, Path cacheRoot, boolean offline) {
        Path zoltToml = zoltDir.resolve("zolt.toml");
        if (!Files.isRegularFile(zoltToml)) {
            throw new ActionableException(
                    "No zolt.toml found at " + zoltDir + " to compare against Maven.",
                    "Run `zolt explain --emit-toml-output <dir>` to synthesize a draft, then pass "
                            + "--zolt-dir <dir> (or add a zolt.toml at the project root).");
        }
        ResolveOptions options = ResolveOptions.offline(offline);
        List<ResolvedModule> modules = new ArrayList<>();
        Map<String, String> memberPaths = new LinkedHashMap<>();
        if (isWorkspace(zoltToml)) {
            Workspace workspace = discoveryService.load(zoltDir);
            for (WorkspaceMember member : workspace.members()) {
                ResolvedModule module = resolveMember(member.config(), cacheRoot, options);
                modules.add(module);
                memberPaths.put(module.moduleKey(), member.path());
            }
        } else {
            ProjectConfig config = tomlParser.parse(zoltToml);
            ResolvedModule module = resolveMember(config, cacheRoot, options);
            modules.add(module);
            memberPaths.put(module.moduleKey(), ".");
        }
        return new ZoltResolution(modules, memberPaths, zoltDir.toString());
    }

    private ResolvedModule resolveMember(ProjectConfig config, Path cacheRoot, ResolveOptions options) {
        ProjectMetadata metadata = config.project();
        List<LockPackage> packages = resolveService
                .resolveLockfile(config, cacheRoot, options)
                .lockfile()
                .packages();
        return moduleMapper.fromLockPackages(
                metadata.group(), metadata.name(), metadata.version(), packages);
    }

    private static boolean isWorkspace(Path zoltToml) {
        try {
            return Files.readAllLines(zoltToml).stream()
                    .map(String::trim)
                    .anyMatch("[workspace]"::equals);
        } catch (IOException exception) {
            throw new ActionableException(
                    "Could not read " + zoltToml + ": " + exception.getMessage() + ".",
                    "Confirm the file is readable and retry `zolt explain verify`.");
        }
    }
}
