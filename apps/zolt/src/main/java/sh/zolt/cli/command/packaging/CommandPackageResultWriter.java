package sh.zolt.cli.command.packaging;

import sh.zolt.build.packaging.PackageArtifact;
import sh.zolt.build.packaging.PackageResult;
import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.project.PackageMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class CommandPackageResultWriter {
    public CommandPackageResultWriter() {
    }

    void print(CommandHumanOutput output, PackageResult result, String suffix) {
        for (OutputLine line : lines(result, suffix)) {
            switch (line.kind()) {
                case SUMMARY -> output.summary(line.message(), line.facts().toArray(new String[0]));
                case SUCCESS -> output.success(line.message());
                case DETAIL -> output.detail(line.message());
                case POINTER -> output.pointer(line.verb(), line.message());
            }
        }
    }

    public List<OutputLine> lines(PackageResult result, String suffix) {
        List<OutputLine> lines = new ArrayList<>();
        lines.add(OutputLine.summary(PackageCommandModes.packageSummary(result) + suffix, List.of()));
        appendRunnableDetails(lines, result, suffix);
        if (suffix.isBlank()) {
            appendPackageModeDetail(lines, result);
        }
        appendUberOverrideDetails(lines, result);
        lines.add(OutputLine.pointer("wrote", result.jarPath().toString()));
        result.runtimeClasspathPath().ifPresent(path ->
                lines.add(OutputLine.pointer("wrote", path.toString())));
        result.evidenceManifestPath().ifPresent(path ->
                lines.add(OutputLine.pointer("wrote", path.toString())));
        for (PackageArtifact artifact : result.artifacts()) {
            lines.add(OutputLine.pointer("wrote", artifact.path().toString()));
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
    }

    private static void appendUberOverrideDetails(List<OutputLine> lines, PackageResult result) {
        if (result.mode() != PackageMode.UBER) {
            return;
        }
        Map<String, Long> overridesByJar = result.mergeDecisions().stream()
                .filter(decision -> "overridden-duplicate".equals(decision.kind()))
                .flatMap(decision -> decision.sources().stream())
                .collect(Collectors.groupingBy(source -> source, TreeMap::new, Collectors.counting()));
        overridesByJar.forEach((jar, count) -> lines.add(OutputLine.detail(
                "Skipped " + count + " duplicate " + (count == 1 ? "entry" : "entries") + " from " + jar)));
    }

    public record OutputLine(OutputLineKind kind, String verb, String message, List<String> facts) {
        static OutputLine summary(String message, List<String> facts) {
            return new OutputLine(OutputLineKind.SUMMARY, "", message, List.copyOf(facts));
        }

        static OutputLine success(String message) {
            return new OutputLine(OutputLineKind.SUCCESS, "", message, List.of());
        }

        static OutputLine detail(String message) {
            return new OutputLine(OutputLineKind.DETAIL, "", message, List.of());
        }

        static OutputLine pointer(String verb, String target) {
            return new OutputLine(OutputLineKind.POINTER, verb, target, List.of());
        }
    }

    public enum OutputLineKind {
        SUMMARY,
        SUCCESS,
        DETAIL,
        POINTER
    }
}
