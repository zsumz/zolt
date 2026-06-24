package com.zolt.cli.command;

import com.zolt.cli.CommandHumanOutput;
import com.zolt.quality.QualityCheckReport;
import com.zolt.quality.QualityCheckResult;
import com.zolt.quality.QualityCheckStatus;
import picocli.CommandLine.Model.CommandSpec;

final class CommandCheckOutput {
    private CommandCheckOutput() {
    }

    static void print(CommandSpec spec, QualityCheckReport report) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        output.work(report.workspace() ? "Checking workspace" : "Checking project");
        for (QualityCheckResult check : report.checks()) {
            output.check(check.status().marker(), checkLine(check));
            if (!check.nextStep().isBlank()) {
                output.line("  next: " + check.nextStep());
            }
        }
        output.success(summary(report));
    }

    private static String checkLine(QualityCheckResult check) {
        StringBuilder line = new StringBuilder();
        line.append(check.id()).append(' ');
        check.member().ifPresent(member -> line.append(member).append(' '));
        line.append(check.subject())
                .append(' ')
                .append(check.message());
        return line.toString();
    }

    private static String summary(QualityCheckReport report) {
        return "Checked "
                + report.checks().size()
                + " quality checks: "
                + report.passedCount()
                + " passed, "
                + warningCount(report)
                + " warnings, "
                + report.failedCount()
                + " failed, "
                + report.skippedCount()
                + " skipped";
    }

    private static long warningCount(QualityCheckReport report) {
        return report.checks().stream()
                .filter(check -> check.status() == QualityCheckStatus.WARNING)
                .count();
    }
}
