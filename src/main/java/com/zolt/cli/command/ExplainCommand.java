package com.zolt.cli.command;

import com.zolt.explain.GradleExplainFormatter;
import com.zolt.explain.GradleInspectionResult;
import com.zolt.explain.GradleStaticProjectInspector;
import com.zolt.explain.MavenExplainFormatter;
import com.zolt.explain.MavenInspectionResult;
import com.zolt.explain.MavenStaticProjectInspector;
import com.zolt.explain.MigrationBlockerReportFormatter;
import com.zolt.explain.MigrationBlockerReports;
import com.zolt.explain.MigrationExplainException;
import com.zolt.explain.MigrationReadinessScorecardFormatter;
import com.zolt.explain.MigrationReadinessScorecards;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "explain",
        mixinStandardHelpOptions = true,
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

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Option(names = "--source", description = "Project source type: auto, maven, or gradle.")
    private Source source = Source.AUTO;

    @Option(names = "--scorecard", description = "Print a migration readiness scorecard instead of the raw explain report.")
    private boolean scorecard;

    @Option(names = "--blockers", description = "Print a focused migration blocker report instead of the raw explain report.")
    private boolean blockers;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        Path root = workingDirectory.toAbsolutePath().normalize();
        if (scorecard && blockers) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "`--scorecard` and `--blockers` select different explain reports. Choose one.");
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
            MavenInspectionResult result = new MavenStaticProjectInspector().inspect(root);
            if (blockers) {
                MigrationBlockerReportFormatter formatter = new MigrationBlockerReportFormatter();
                if (format == Format.JSON) {
                    CommandOutput.printAndFlush(
                            spec,
                            formatter.json(MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                } else {
                    CommandOutput.printAndFlush(
                            spec,
                            formatter.text(MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                }
                return 0;
            }
            if (scorecard) {
                MigrationReadinessScorecardFormatter formatter = new MigrationReadinessScorecardFormatter();
                if (format == Format.JSON) {
                    CommandOutput.printAndFlush(spec, formatter.json(MigrationReadinessScorecards.from(result)));
                } else {
                    CommandOutput.printAndFlush(spec, formatter.text(MigrationReadinessScorecards.from(result)));
                }
                return 0;
            }
            MavenExplainFormatter formatter = new MavenExplainFormatter();
            if (format == Format.JSON) {
                CommandOutput.printAndFlush(spec, formatter.json(result));
            } else {
                CommandOutput.printAndFlush(spec, formatter.text(result));
            }
            return 0;
        } catch (MigrationExplainException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private Integer explainGradle(Path root) {
        try {
            GradleInspectionResult result = new GradleStaticProjectInspector().inspect(root);
            if (blockers) {
                MigrationBlockerReportFormatter formatter = new MigrationBlockerReportFormatter();
                if (format == Format.JSON) {
                    CommandOutput.printAndFlush(
                            spec,
                            formatter.json(MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                } else {
                    CommandOutput.printAndFlush(
                            spec,
                            formatter.text(MigrationBlockerReports.from(MigrationReadinessScorecards.from(result))));
                }
                return 0;
            }
            if (scorecard) {
                MigrationReadinessScorecardFormatter formatter = new MigrationReadinessScorecardFormatter();
                if (format == Format.JSON) {
                    CommandOutput.printAndFlush(spec, formatter.json(MigrationReadinessScorecards.from(result)));
                } else {
                    CommandOutput.printAndFlush(spec, formatter.text(MigrationReadinessScorecards.from(result)));
                }
                return 0;
            }
            GradleExplainFormatter formatter = new GradleExplainFormatter();
            if (format == Format.JSON) {
                CommandOutput.printAndFlush(spec, formatter.json(result));
            } else {
                CommandOutput.printAndFlush(spec, formatter.text(result));
            }
            return 0;
        } catch (MigrationExplainException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private Integer explainPlaceholder(Path root, Source detectedSource) {
        if (format == Format.JSON) {
            spec.commandLine().getOut().println("""
                    {"schemaVersion":1,"command":"explain","status":"not-implemented","source":"%s","root":"%s","message":"zolt explain is a future migration-audit command. It will inspect Maven and Gradle metadata statically without executing Maven or Gradle.","nextStep":"Track implementation in followUps/-add-zolt-explain-command-scaffold.md through followUps/-add-migration-explain-fixtures-and-golden-tests.md."}
                    """.formatted(detectedSource.name().toLowerCase(), jsonEscape(root.toString())).stripTrailing());
            return 1;
        }
        spec.commandLine().getOut().println("""
                zolt explain is not implemented yet.

                Planned behavior:
                  - audit Maven and Gradle project metadata statically
                  - report what Zolt can build, test, package, and cache
                  - report non-determinism and migration blockers
                  - emit deterministic text or JSON reports

                This command will not execute Maven or Gradle and will not create compatibility mode.

                Requested source: %s
                Project root: %s
                Track this work in followUps/-add-zolt-explain-command-scaffold.md through followUps/-add-migration-explain-fixtures-and-golden-tests.md.
                """.formatted(detectedSource.name().toLowerCase(), root).stripTrailing());
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
