package com.zolt.cli.command;

import com.zolt.build.BuildException;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavacException;
import com.zolt.build.ManifestGenerationException;
import com.zolt.build.PackageException;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.resolve.ResolveException;
import com.zolt.selfhost.SelfHostingParityException;
import com.zolt.selfhost.SelfHostingParityResult;
import com.zolt.selfhost.SelfHostingParityService;
import com.zolt.toml.ZoltConfigException;
import java.nio.file.Path;
import java.util.Set;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "self-parity", description = "Compare bootstrap and Zolt-built jar entries.")
public final class SelfParityCommand implements Runnable {
    private final SelfHostingParityService selfHostingParityService;

    @Option(names = "--bootstrap-jar", required = true, description = "Bootstrap-built jar to compare against.")
    private Path bootstrapJar;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

    @Spec
    private CommandSpec spec;

    public SelfParityCommand() {
        this(new SelfHostingParityService());
    }

    SelfParityCommand(SelfHostingParityService selfHostingParityService) {
        this.selfHostingParityService = selfHostingParityService;
    }

    @Override
    public void run() {
        try {
            SelfHostingParityResult result = selfHostingParityService.compare(workingDirectory, cacheRoot, bootstrapJar);
            if (!result.ok()) {
                spec.commandLine().getErr().println("error: Self-hosting parity failed: bootstrap jar and Zolt-built jar contents differ.");
                spec.commandLine().getErr().println("Missing from Zolt-built jar:");
                spec.commandLine().getErr().print(formatEntries(result.missingFromZolt()));
                spec.commandLine().getErr().println("Extra in Zolt-built jar:");
                spec.commandLine().getErr().print(formatEntries(result.extraInZolt()));
                throw new CommandLine.ExecutionException(spec.commandLine(), "Self-hosting parity failed.");
            }
            spec.commandLine().getOut().println("Self-hosting parity status: ok");
            spec.commandLine().getOut().println("Bootstrap jar: " + result.bootstrapJar());
            spec.commandLine().getOut().println("Zolt-built jar: " + result.zoltJar());
            spec.commandLine().getOut().println("Jar entries match");
        } catch (BuildException
                | JavacException
                | GroovyCompileException
                | ManifestGenerationException
                | PackageException
                | ResourceCopyException
                | SelfHostingParityException
                | SourceDiscoveryException
                | LockfileReadException
                | ResolveException
                | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private static String formatEntries(Set<String> entries) {
        if (entries.isEmpty()) {
            return "  <none>\n";
        }
        StringBuilder output = new StringBuilder();
        entries.stream()
                .limit(50)
                .forEach(entry -> output.append("  - ").append(entry).append('\n'));
        if (entries.size() > 50) {
            output.append("  ... ").append(entries.size() - 50).append(" more\n");
        }
        return output.toString();
    }
}
