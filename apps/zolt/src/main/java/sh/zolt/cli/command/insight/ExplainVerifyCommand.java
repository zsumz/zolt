package sh.zolt.cli.command.insight;

import sh.zolt.cache.LocalArtifactCache;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandFrameworkServices;
import sh.zolt.cli.command.CommandOutput;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.cli.command.insight.ZoltResolutionLoader.ZoltResolution;
import sh.zolt.error.ActionableException;
import sh.zolt.explain.verify.MavenDependencyTreeParser;
import sh.zolt.explain.verify.ResolvedModule;
import sh.zolt.explain.verify.VerifyComparator;
import sh.zolt.explain.verify.VerifyReport;
import sh.zolt.explain.verify.VerifyReportFormatter;
import sh.zolt.explain.verify.VerifyReportJsonWriter;
import sh.zolt.resolve.ResolveException;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.workspace.WorkspaceConfigException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * {@code zolt explain verify}: runs the project's Maven and Zolt's resolver over the same project and
 * reports, per module and per scope, exactly how their resolved dependency sets differ — matched,
 * version drift, only-in-Maven, only-in-Zolt — so a migration can be verified against facts.
 *
 * <p>The report states facts and counts only. Zolt uses highest-version-wins mediation while Maven
 * uses nearest-wins, so some drift is expected; the command does not editorialize about equivalence.
 * Exit code is 0 when the resolved sets are identical across every module and scope, and non-zero when
 * any module is one-sided or any scope shows drift or a one-sided artifact — which makes it usable as
 * a CI gate.
 */
@Command(
        name = "verify",
        description = "Compare Maven-resolved and Zolt-resolved dependencies per module and scope.")
public final class ExplainVerifyCommand implements Callable<Integer> {
    enum Format {
        TEXT,
        JSON
    }

    private final MavenDependencyTreeRunner mavenRunner;
    private final MavenDependencyTreeParser treeParser;
    private final MavenModuleDirectories mavenModuleDirectories;
    private final ZoltResolutionLoader zoltLoader;
    private final VerifyComparator comparator;
    private final VerifyReportFormatter formatter;
    private final VerifyReportJsonWriter jsonWriter;

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Option(
            names = "--zolt-dir",
            paramLabel = "<DIRECTORY>",
            description = "Directory of the Zolt project/workspace to compare. Defaults to the Maven project root.")
    private Path zoltDir;

    @Option(
            names = "--offline",
            description = "Resolve Zolt dependencies from the local cache only (no downloads).")
    private boolean offline;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Spec
    private CommandSpec spec;

    public ExplainVerifyCommand() {
        this(
                new MavenDependencyTreeRunner(),
                new MavenDependencyTreeParser(),
                new MavenModuleDirectories(),
                new ZoltResolutionLoader(CommandFrameworkServices.resolveCommandServices().resolveService()),
                new VerifyComparator(),
                new VerifyReportFormatter(),
                new VerifyReportJsonWriter());
    }

    ExplainVerifyCommand(
            MavenDependencyTreeRunner mavenRunner,
            MavenDependencyTreeParser treeParser,
            MavenModuleDirectories mavenModuleDirectories,
            ZoltResolutionLoader zoltLoader,
            VerifyComparator comparator,
            VerifyReportFormatter formatter,
            VerifyReportJsonWriter jsonWriter) {
        this.mavenRunner = mavenRunner;
        this.treeParser = treeParser;
        this.mavenModuleDirectories = mavenModuleDirectories;
        this.zoltLoader = zoltLoader;
        this.comparator = comparator;
        this.formatter = formatter;
        this.jsonWriter = jsonWriter;
    }

    @Override
    public Integer call() {
        Path mavenRoot = projectDirectory.path().toAbsolutePath().normalize();
        Path resolvedZoltDir = (zoltDir == null ? mavenRoot : zoltDir).toAbsolutePath().normalize();
        try {
            String treeText = mavenRunner.run(mavenRoot);
            List<ResolvedModule> mavenModules = treeParser.parse(treeText);
            Map<String, String> mavenDirectories = mavenModuleDirectories.resolve(mavenRoot);
            ZoltResolution zolt = zoltLoader.load(resolvedZoltDir, cacheRoot, offline);
            VerifyReport report = comparator.compare(
                    mavenRoot.toString(),
                    zolt.root(),
                    mavenModules,
                    zolt.modules(),
                    mavenDirectories,
                    zolt.memberPaths());
            if (format == Format.JSON) {
                CommandOutput.printAndFlush(spec, jsonWriter.json(report));
            } else {
                CommandOutput.printAndFlush(spec, formatter.text(report));
            }
            return report.hasDifferences() ? 1 : 0;
        } catch (ActionableException
                | ResolveException
                | ZoltConfigException
                | WorkspaceConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
