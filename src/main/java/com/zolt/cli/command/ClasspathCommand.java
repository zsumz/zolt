package com.zolt.cli.command;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathFormatter;
import com.zolt.classpath.ClasspathLaneAuditFormatter;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "classpath", description = "Print a classpath from zolt.lock.")
public final class ClasspathCommand implements Runnable {
    enum Kind {
        COMPILE("compile"),
        RUNTIME("runtime"),
        TEST("test"),
        PROCESSOR("processor"),
        TEST_PROCESSOR("test-processor"),
        QUARKUS_DEPLOYMENT("quarkus-deployment"),
        AUDIT("audit");

        private static final String SUPPORTED =
                "compile, runtime, test, processor, test-processor, quarkus-deployment, or audit";

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        private static Kind parse(String value) {
            for (Kind kind : values()) {
                if (kind.label.equalsIgnoreCase(value)) {
                    return kind;
                }
            }
            throw new ClasspathCommandException(
                    "Unknown classpath kind `" + value
                            + "`. Use " + SUPPORTED + ".");
        }
    }

    enum Format {
        TEXT,
        JSON
    }

    @Parameters(
            index = "0",
            paramLabel = "compile|runtime|test|processor|test-processor|quarkus-deployment|audit",
            description = "Classpath kind to print, or audit to inspect all Zolt-owned lanes.")
    private String kind;

    @Option(names = "--format", description = "Output format for audit: text or json.")
    private Format format = Format.TEXT;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = com.zolt.cache.LocalArtifactCache.defaultRoot();

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        try {
            ZoltLockfileReader lockfileReader = new ZoltLockfileReader();
            Path configPath = workingDirectory.resolve("zolt.toml");
            if (Files.isRegularFile(configPath)) {
                ProjectConfig config = new ZoltTomlParser().parse(configPath);
                CommandLockfiles.requireFreshLockfile(workingDirectory, config, cacheRoot, false);
            }
            ZoltLockfile lockfile = lockfileReader.read(workingDirectory.resolve("zolt.lock"));
            Kind parsedKind = Kind.parse(kind);
            if (parsedKind == Kind.AUDIT) {
                ClasspathLaneAuditFormatter formatter = new ClasspathLaneAuditFormatter();
                String output = format == Format.JSON
                        ? formatter.formatJson(lockfile)
                        : formatter.formatText(lockfile);
                CommandOutput.printAndFlush(spec, output);
                return;
            }
            if (format == Format.JSON) {
                throw new ClasspathCommandException(
                        "`zolt classpath --format json` is supported for `audit` only. "
                                + "Use `zolt classpath audit --format json`.");
            }
            ClasspathSet classpaths = new ClasspathBuilder().build(lockfileReader.classpathPackages(lockfile, cacheRoot));
            String output = new ClasspathFormatter().format(switch (parsedKind) {
                case COMPILE -> classpaths.compile();
                case RUNTIME -> classpaths.runtime();
                case TEST -> classpaths.test();
                case PROCESSOR -> classpaths.processor();
                case TEST_PROCESSOR -> classpaths.testProcessor();
                case QUARKUS_DEPLOYMENT -> classpaths.quarkusDeployment();
                case AUDIT -> throw new ClasspathCommandException("Classpath audit should be handled before formatting.");
            });
            CommandOutput.printAndFlush(spec, output);
        } catch (ArtifactCacheException
                | ClasspathCommandException
                | LockfileReadException
                | ResolveException
                | ZoltConfigException exception) {
            spec.commandLine().getErr().println("error: " + exception.getMessage());
            throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
        }
    }

    private static final class ClasspathCommandException extends RuntimeException {
        private ClasspathCommandException(String message) {
            super(message);
        }
    }
}
