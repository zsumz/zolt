package sh.zolt.cli.command.insight;

import sh.zolt.cache.LocalArtifactCache;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandFrameworkServices;
import sh.zolt.cli.command.CommandOutput;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.cli.command.insight.ZoltResolutionLoader.ZoltResolution;
import sh.zolt.error.ActionableException;
import sh.zolt.explain.verify.BuildTool;
import sh.zolt.explain.verify.GradleDependencyTreeParser;
import sh.zolt.explain.verify.MavenDependencyTreeParser;
import sh.zolt.explain.verify.ResolvedModule;
import sh.zolt.explain.verify.VerifyComparator;
import sh.zolt.explain.verify.VerifyReport;
import sh.zolt.explain.verify.VerifyReportFormatter;
import sh.zolt.explain.verify.VerifyReportJsonWriter;
import sh.zolt.resolve.ResolveException;
import sh.zolt.resolve.materialization.RepositoryOverlay;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.workspace.WorkspaceConfigException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * {@code zolt explain verify}: runs the project's incumbent build (Maven or Gradle, auto-detected) and
 * Zolt's resolver over the same project and reports, per module and per scope, exactly how their
 * resolved dependency sets differ — matched, version drift, only-in-incumbent, only-in-Zolt — so a
 * migration can be verified against facts.
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

    enum Source {
        AUTO,
        MAVEN,
        GRADLE
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
            names = "--source",
            description = "Incumbent build tool to compare against: auto, maven, or gradle.")
    private Source source = Source.AUTO;

    @Option(
            names = "--zolt-dir",
            paramLabel = "<DIRECTORY>",
            description = "Directory of the Zolt project/workspace to compare. Defaults to the Maven project root.")
    private Path zoltDir;

    @Option(
            names = "--offline",
            description = "Resolve Zolt dependencies from the local cache only (no downloads).")
    private boolean offline;

    @Option(
            names = "--repository-overlay",
            description = "Opt into a user-local repository overlay for the Zolt resolution. "
                    + "Supported values: maven-local, local-maven.")
    private List<String> repositoryOverlays = List.of();

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Option(names = "--maven-local-root", hidden = true)
    private Path mavenLocalRoot = Path.of(System.getProperty("user.home"), ".m2", "repository");

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
            BuildTool buildTool = detectBuildTool(mavenRoot);
            Incumbent incumbent = extractIncumbent(buildTool, mavenRoot);
            ZoltResolution zolt = zoltLoader.load(resolvedZoltDir, cacheRoot, offline, configuredOverlays());
            VerifyReport report = comparator.compare(
                    buildTool,
                    mavenRoot.toString(),
                    zolt.root(),
                    incumbent.modules(),
                    zolt.modules(),
                    incumbent.directories(),
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

    private List<RepositoryOverlay> configuredOverlays() {
        List<RepositoryOverlay> overlays = new ArrayList<>();
        for (String overlay : repositoryOverlays) {
            overlays.add(repositoryOverlay(overlay));
        }
        return overlays;
    }

    private RepositoryOverlay repositoryOverlay(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "maven-local", "local-maven" -> RepositoryOverlay.mavenLocal(mavenLocalRoot);
            default -> throw ResolveException.actionable(
                    "Unsupported repository overlay `" + value + "`.",
                    "Supported overlays: maven-local.");
        };
    }

    /**
     * Resolves which incumbent build tool to compare against. Explicit {@code --source} wins; otherwise
     * a {@code pom.xml} selects Maven (Maven precedence, mirroring {@code zolt explain --source auto})
     * and a Gradle settings/build script selects Gradle. When neither is present the Maven path runs so
     * its "no pom.xml" guidance is what the user sees.
     */
    private BuildTool detectBuildTool(Path root) {
        if (source == Source.MAVEN) {
            return BuildTool.MAVEN;
        }
        if (source == Source.GRADLE) {
            return BuildTool.GRADLE;
        }
        if (Files.isRegularFile(root.resolve("pom.xml"))) {
            return BuildTool.MAVEN;
        }
        if (hasGradleBuild(root)) {
            return BuildTool.GRADLE;
        }
        return BuildTool.MAVEN;
    }

    private Incumbent extractIncumbent(BuildTool buildTool, Path root) {
        if (buildTool == BuildTool.GRADLE) {
            GradleProjectDiscovery.GradleProjects projects = new GradleProjectDiscovery().discover(root);
            String reportText = new GradleDependenciesRunner().run(root, projects.projectPaths(), offline);
            List<ResolvedModule> modules = new GradleDependencyTreeParser().parse(reportText, projects.byPath());
            return new Incumbent(modules, projects.directories());
        }
        String treeText = mavenRunner.run(root);
        return new Incumbent(treeParser.parse(treeText), mavenModuleDirectories.resolve(root));
    }

    private static boolean hasGradleBuild(Path root) {
        return Files.isRegularFile(root.resolve("settings.gradle"))
                || Files.isRegularFile(root.resolve("settings.gradle.kts"))
                || Files.isRegularFile(root.resolve("build.gradle"))
                || Files.isRegularFile(root.resolve("build.gradle.kts"));
    }

    /** The incumbent side of the comparison: resolved modules and their directories. */
    private record Incumbent(List<ResolvedModule> modules, Map<String, String> directories) {
    }
}
