package com.zolt.cli.command.packaging;

import com.zolt.build.PackageArtifact;
import com.zolt.build.PackageResult;
import com.zolt.cli.CommandHumanOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CommandPackageResultWriter {
    public CommandPackageResultWriter() {
    }

    void print(CommandHumanOutput output, PackageResult result, String suffix) {
        for (OutputLine line : lines(result, suffix)) {
            switch (line.kind()) {
                case SUCCESS -> output.success(line.message());
                case DETAIL -> output.detail(line.message());
            }
        }
    }

    public List<OutputLine> lines(PackageResult result, String suffix) {
        List<OutputLine> lines = new ArrayList<>();
        lines.add(OutputLine.success(PackageCommandModes.packageSummary(result) + suffix));
        appendRunnableDetails(lines, result, suffix);
        if (suffix.isBlank()) {
            appendPackageModeDetail(lines, result);
        }
        lines.add(OutputLine.detail("Wrote archive to " + result.jarPath()));
        result.evidenceManifestPath().ifPresent(path ->
                lines.add(OutputLine.detail("Wrote package evidence to " + path)));
        for (PackageArtifact artifact : result.artifacts()) {
            lines.add(OutputLine.detail("Wrote " + artifact.classifier() + " jar to " + artifact.path()));
        }
        return List.copyOf(lines);
    }

    private static void appendRunnableDetails(List<OutputLine> lines, PackageResult result, String suffix) {
        if (result.hasMainClass()) {
            lines.add(OutputLine.detail(suffix.isBlank()
                    ? "Included Main-Class manifest entry"
                    : "Included Main-Class manifest entry" + suffix));
            if (suffix.isBlank()) {
                lines.add(OutputLine.detail("Run with: java -jar " + result.jarPath()));
                PackageCommandModes.runInstruction(result)
                        .ifPresent(message -> lines.add(OutputLine.detail(message)));
            }
        } else if (suffix.isBlank()) {
            Optional<String> noMainClassDetail = PackageCommandModes.noMainClassDetail(result);
            lines.add(OutputLine.detail(noMainClassDetail.orElse(
                    "No Main-Class manifest entry; add [project].main to make the jar directly runnable.")));
        }
    }

    private static void appendPackageModeDetail(List<OutputLine> lines, PackageResult result) {
        PackageCommandModes.PackageModeDetail detail = PackageCommandModes.packageModeDetail(result);
        lines.add(OutputLine.detail(detail.message()));
        detail.secondaryMessage().ifPresent(message -> lines.add(OutputLine.detail(message)));
    }

    public record OutputLine(OutputLineKind kind, String message) {
        static OutputLine success(String message) {
            return new OutputLine(OutputLineKind.SUCCESS, message);
        }

        static OutputLine detail(String message) {
            return new OutputLine(OutputLineKind.DETAIL, message);
        }
    }

    public enum OutputLineKind {
        SUCCESS,
        DETAIL
    }
}
