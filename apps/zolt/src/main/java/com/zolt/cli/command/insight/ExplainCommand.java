package com.zolt.cli.command.insight;

import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.command.CommandFailures;
import com.zolt.cli.command.CommandOutput;
import com.zolt.cli.command.CommandProjectDirectory;
import com.zolt.explain.maven.MavenExplainFormatter;
import com.zolt.explain.maven.MavenInspectionResult;
import com.zolt.explain.maven.MavenStaticProjectInspector;
import com.zolt.explain.MigrationBlockerReportFormatter;
import com.zolt.explain.MigrationBlockerReports;
import com.zolt.explain.MigrationExplainException;
import com.zolt.explain.MigrationReadinessScorecardFormatter;
import com.zolt.explain.MigrationReadinessScorecards;
import com.zolt.explain.emit.DraftWorkspaceRenderer;
import com.zolt.explain.emit.DraftZoltTomlRenderer;
import com.zolt.explain.emit.EmitRenderer;
import com.zolt.explain.emit.InspectionToProjectConfig;
import com.zolt.explain.gradle.GradleExplainFormatter;
import com.zolt.explain.gradle.GradleInspectionResult;
import com.zolt.explain.gradle.GradleStaticProjectInspector;
import com.zolt.toml.ZoltTomlWriter;
import com.zolt.workspace.toml.WorkspaceTomlWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine;

@Command(
        name = "explain",
        description = "Audit a Maven or Gradle project for future Zolt migration.")
public final class ExplainCommand implements Callable<Integer> {
    enum Format {
        TEXT,
        JSON
    }

    enum Source {
        AUTO,
        MAVEN,
        GRADLE
    }

    private final MavenStaticProjectInspector mavenInspector;
    private final GradleStaticProjectInspector gradleInspector;
    private final MavenExplainFormatter mavenExplainFormatter;
    private final GradleExplainFormatter gradleExplainFormatter;
    private final MigrationBlockerReportFormatter blockerReportFormatter;
    private final MigrationReadinessScorecardFormatter scorecardFormatter;
    private final EmitRenderer emitRenderer;

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Option(names = "--source", description = "Project source type: auto, maven, or gradle.")
    private Source source = Source.AUTO;

    @Option(names = "--scorecard", description = "Print a migration readiness scorecard instead of the raw explain report.")
    private boolean scorecard;

    @Option(names = "--blockers", description = "Print a focused migration blocker report instead of the raw explain report.")
    private boolean blockers;

    @Option(names = "--emit-toml", description = "Print a draft zolt.toml synthesized from the migration audit instead of the raw explain report.")
    private boolean emitToml;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Spec
    private CommandSpec spec;

    public ExplainCommand() {
        this(
                new MavenStaticProjectInspector(),
                new GradleStaticProjectInspector(),
                new MavenExplainFormatter(),
                new GradleExplainFormatter(),
                new MigrationBlockerReportFormatter(),
                new MigrationReadinessScorecardFormatter(),
                new EmitRenderer(
                        new InspectionToProjectConfig(),
                        new DraftZoltTomlRenderer(),
                        new DraftWorkspaceRenderer(),
                        new ZoltTomlWriter()::write,
                        new WorkspaceTomlWriter()::write));
    }

    ExplainCommand(
            MavenStaticProjectInspector mavenInspector,
            GradleStaticProjectInspector gradleInspector,
            MavenExplainFormatter mavenExplainFormatter,
            GradleExplainFormatter gradleExplainFormatter,
            MigrationBlockerReportFormatter blockerReportFormatter,
            MigrationReadinessScorecardFormatter scorecardFormatter,
            EmitRenderer emitRenderer) {
        this.mavenInspector = mavenInspector;
        this.gradleInspector = gradleInspector;
        this.mavenExplainFormatter = mavenExplainFormatter;
        this.gradleExplainFormatter = gradleExplainFormatter;
        this.blockerReportFormatter = blockerReportFormatter;
        this.scorecardFormatter = scorecardFormatter;
        this.emitRenderer = emitRenderer;
    }

    @Override
    public Integer call() {
        Path root = projectDirectory.path().toAbsolutePath().normalize();
        if (scorecard && blockers) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "`--scorecard` and `--blockers` select different explain reports. Choose one.");
        }
        if (emitToml && (scorecard || blockers)) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "`--emit-toml` emits a draft zolt.toml and cannot combine with "
                            + (scorecard ? "`--scorecard`" : "`--blockers`")
                            + ". Choose one.");
        }
        Source detectedSource = detectSource(root);
        if (detectedSource == Source.MAVEN) {
            return explainMaven(root);
        }
        if (detectedSource == Source.GRADLE) {
            return explainGradle(root);
        }
        return explainPlaceholder(root, detectedSource);
    }

    private Integer explainMaven(Path root) {
        try {
            MavenInspectionResult result = mavenInspector.inspect(root);
            if (emitToml) {
                CommandOutput.printAndFlush(spec, emitRenderer.renderMaven(result).stripTrailing());
                return 0;
            }
            if (blockers) {
                if (format == Format.JSON) {
                    CommandOutput.printAndFlush(
                            spec,
                            blockerReportFormatter.json(
                                    MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                } else {
                    CommandOutput.printAndFlush(
                            spec,
                            blockerReportFormatter.text(
                                    MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                }
                return 0;
            }
            if (scorecard) {
                if (format == Format.JSON) {
                    CommandOutput.printAndFlush(spec, scorecardFormatter.json(MigrationReadinessScorecards.from(result)));
                } else {
                    CommandOutput.printAndFlush(spec, scorecardFormatter.text(MigrationReadinessScorecards.from(result)));
                }
                return 0;
            }
            if (format == Format.JSON) {
                CommandOutput.printAndFlush(spec, mavenExplainFormatter.json(result));
            } else {
                CommandOutput.printAndFlush(spec, mavenExplainFormatter.text(result));
            }
            return 0;
        } catch (MigrationExplainException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private Integer explainGradle(Path root) {
        try {
            GradleInspectionResult result = gradleInspector.inspect(root);
            if (emitToml) {
                CommandOutput.printAndFlush(spec, emitRenderer.renderGradle(result).stripTrailing());
                return 0;
            }
            if (blockers) {
                if (format == Format.JSON) {
                    CommandOutput.printAndFlush(
                            spec,
                            blockerReportFormatter.json(
                                    MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                } else {
                    CommandOutput.printAndFlush(
                            spec,
                            blockerReportFormatter.text(
                                    MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                }
                return 0;
            }
            if (scorecard) {
                if (format == Format.JSON) {
                    CommandOutput.printAndFlush(spec, scorecardFormatter.json(MigrationReadinessScorecards.from(result)));
                } else {
                    CommandOutput.printAndFlush(spec, scorecardFormatter.text(MigrationReadinessScorecards.from(result)));
                }
                return 0;
            }
            if (format == Format.JSON) {
                CommandOutput.printAndFlush(spec, gradleExplainFormatter.json(result));
            } else {
                CommandOutput.printAndFlush(spec, gradleExplainFormatter.text(result));
            }
            return 0;
        } catch (MigrationExplainException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private Integer explainPlaceholder(Path root, Source detectedSource) {
        String nextStep = "Run zolt explain against a Maven (pom.xml) or Gradle "
                + "(settings.gradle[.kts]/build.gradle[.kts]) project, or pass --directory <path> "
                + "or --source maven|gradle to point it at one.";
        if (format == Format.JSON) {
            CommandOutput.printAndFlush(spec, """
                    {"schemaVersion":1,"command":"explain","status":"no-project","source":"%s","root":"%s","message":"%s","nextStep":"%s"}
                    """.formatted(
                            detectedSource.name().toLowerCase(),
                            jsonEscape(root.toString()),
                            jsonEscape("No Maven or Gradle metadata was found at " + root + "."),
                            jsonEscape(nextStep))
                    .stripTrailing());
            return 1;
        }
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        output.work("No Maven or Gradle metadata found at " + root + ".");
        output.blankLine();
        output.line("zolt explain audits Maven and Gradle metadata statically. It looks for:");
        output.line("  - a Maven pom.xml");
        output.line("  - a Gradle settings.gradle[.kts] or build.gradle[.kts]");
        output.blankLine();
        output.line("None were detected at this project root, so there is nothing to audit here.");
        output.blankLine();
        output.context("Requested source", detectedSource.name().toLowerCase());
        output.context("Project root", root.toString());
        output.next(nextStep);
        return 1;
    }

    private Source detectSource(Path root) {
        if (source != Source.AUTO) {
            return source;
        }
        if (Files.isRegularFile(root.resolve("pom.xml"))) {
            return Source.MAVEN;
        }
        if (Files.isRegularFile(root.resolve("settings.gradle"))
                || Files.isRegularFile(root.resolve("settings.gradle.kts"))
                || Files.isRegularFile(root.resolve("build.gradle"))
                || Files.isRegularFile(root.resolve("build.gradle.kts"))) {
            return Source.GRADLE;
        }
        return Source.AUTO;
    }

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
