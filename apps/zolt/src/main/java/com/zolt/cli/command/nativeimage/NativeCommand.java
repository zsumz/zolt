package com.zolt.cli.command.nativeimage;

import com.zolt.build.BuildException;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavacException;
import com.zolt.build.ManifestGenerationException;
import com.zolt.build.PackageException;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.build.nativeimage.NativeBuildResult;
import com.zolt.build.nativeimage.NativeBuildService;
import com.zolt.build.NativeImageException;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.CommandProgress;
import com.zolt.cli.command.CommandFailures;
import com.zolt.cli.command.CommandFrameworkServices;
import com.zolt.cli.command.CommandLockfiles;
import com.zolt.cli.command.CommandProjectDirectory;
import com.zolt.cli.command.CommandServiceBundles.CommandNativeServices;
import com.zolt.cli.command.CommandWorkspaceSelections;
import com.zolt.cli.console.ProgressWriter;
import com.zolt.lockfile.toml.LockfileReadException;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.packaging.WorkspaceNativeBuildResult;
import com.zolt.workspace.packaging.WorkspaceNativeBuildService;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "native",
        description = "Build a native binary with GraalVM Native Image.")
public final class NativeCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final NativeBuildService nativeBuildService;
    private final WorkspaceNativeBuildService workspaceNativeBuildService;
    private final CommandLockfiles lockfiles;

    @Option(names = "--native-image", description = "Path to the native-image executable.")
    private Path nativeImageExecutable;

    @Option(names = "--workspace", description = "Build native binaries for selected workspace members.")
    private boolean workspace;

    @Option(names = "--all", description = "Select every workspace member.")
    private boolean all;

    @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
    private List<String> members = List.of();

    @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
    private List<String> memberGroups = List.of();

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

    @Spec
    private CommandSpec spec;

    public NativeCommand() {
        this(CommandFrameworkServices.nativeCommandServices());
    }

    private NativeCommand(CommandNativeServices services) {
        this(
                services.tomlParser(),
                services.nativeBuildService(),
                services.workspaceNativeBuildService(),
                new CommandLockfiles());
    }

    NativeCommand(
            ZoltTomlParser tomlParser,
            NativeBuildService nativeBuildService,
            WorkspaceNativeBuildService workspaceNativeBuildService,
            CommandLockfiles lockfiles) {
        this.tomlParser = tomlParser;
        this.nativeBuildService = nativeBuildService;
        this.workspaceNativeBuildService = workspaceNativeBuildService;
        this.lockfiles = lockfiles;
    }

    @Override
    public void run() {
        ProgressWriter progress = CommandProgress.human(spec);
        Path projectRoot = projectDirectory.path();
        try {
            if (workspace) {
                lockfiles.requireFreshWorkspaceLockfile(projectRoot, cacheRoot, false);
                progress.start("Building workspace native images");
                WorkspaceNativeBuildResult result = workspaceNativeBuildService.buildNative(
                        projectRoot,
                        cacheRoot,
                        CommandWorkspaceSelections.from(all, members, memberGroups),
                        nativeImageExecutable,
                        nativeImageProgress(progress));
                CommandHumanOutput output = CommandHumanOutput.of(spec);
                if (result.resolvedLockfile()) {
                    output.success("Resolved workspace dependencies because zolt.lock was missing");
                }
                for (WorkspaceNativeBuildResult.MemberNativeBuildResult member : result.members()) {
                    output.success("Built native binary at "
                            + member.result().nativeImageResult().outputBinary()
                            + " in "
                            + member.member());
                    output.detail("Preserved Native Image log at "
                            + member.result().nativeImageResult().logFile()
                            + " in "
                            + member.member());
                    printSpringBootAotEvidence(member.result(), " in " + member.member());
                }
                output.success("Built native binaries for " + result.members().size() + " workspace members");
                progress.result("Built native binaries for " + result.members().size() + " workspace members");
                return;
            }
            ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
            progress.start("Building native image");
            NativeBuildResult result = nativeBuildService.buildNative(
                    projectRoot,
                    config,
                    cacheRoot,
                    nativeImageExecutable,
                    nativeImageProgress(progress));
            CommandHumanOutput output = CommandHumanOutput.of(spec);
            if (result.packageResult().buildResult().resolvedLockfile()) {
                output.success("Resolved dependencies because zolt.lock was missing");
            }
            output.success("Built native binary at "
                    + result.nativeImageResult().outputBinary());
            output.detail("Preserved Native Image log at "
                    + result.nativeImageResult().logFile());
            printSpringBootAotEvidence(result, "");
            progress.result("Built native binary at " + result.nativeImageResult().outputBinary());
        } catch (BuildException
                | JavacException
                | GroovyCompileException
                | ManifestGenerationException
                | NativeImageException
                | PackageException
                | ResourceCopyException
                | SourceDiscoveryException
                | LockfileReadException
                | ResolveException
                | WorkspaceConfigException
                | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private void printSpringBootAotEvidence(NativeBuildResult result, String suffix) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        result.springBootAotEvidencePath().ifPresent(path ->
                output.detail("Spring Boot AOT evidence written to " + path + suffix));
    }

    private static Runnable nativeImageProgress(ProgressWriter progress) {
        return () -> progress.heartbeat("Still running: Native Image");
    }
}
