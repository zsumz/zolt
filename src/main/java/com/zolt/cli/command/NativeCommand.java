package com.zolt.cli.command;

import com.zolt.build.BuildException;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavacException;
import com.zolt.build.ManifestGenerationException;
import com.zolt.build.NativeBuildResult;
import com.zolt.build.NativeBuildService;
import com.zolt.build.NativeImageException;
import com.zolt.build.PackageException;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "native", description = "Build a native binary with GraalVM Native Image.")
public final class NativeCommand implements Runnable {
    @Option(names = "--native-image", description = "Path to the native-image executable.")
    private Path nativeImageExecutable;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        try {
            ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
            NativeBuildResult result = new NativeBuildService().buildNative(
                    workingDirectory,
                    config,
                    cacheRoot,
                    nativeImageExecutable);
            if (result.packageResult().buildResult().resolvedLockfile()) {
                spec.commandLine().getOut().println("Resolved dependencies because zolt.lock was missing");
            }
            spec.commandLine().getOut().println("Built native binary at "
                    + result.nativeImageResult().outputBinary());
            spec.commandLine().getOut().println("Native Image log written to "
                    + result.nativeImageResult().logFile());
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
                | ZoltConfigException exception) {
            spec.commandLine().getErr().println("error: " + exception.getMessage());
            throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
        }
    }
}
