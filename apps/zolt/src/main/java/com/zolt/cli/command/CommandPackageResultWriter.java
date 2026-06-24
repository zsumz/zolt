package com.zolt.cli.command;

import com.zolt.build.PackageArtifact;
import com.zolt.build.PackageResult;
import com.zolt.cli.CommandHumanOutput;
import java.io.PrintWriter;
import java.util.Optional;

final class CommandPackageResultWriter {
    void print(CommandHumanOutput output, PackageResult result, String suffix) {
        output.success(CommandPackageSupport.packageSummary(result) + suffix);
        if (result.hasMainClass()) {
            output.detail(suffix.isBlank()
                    ? "Included Main-Class manifest entry"
                    : "Included Main-Class manifest entry" + suffix);
            if (suffix.isBlank()) {
                output.detail("Run with: java -jar " + result.jarPath());
                CommandPackageSupport.runInstruction(result)
                        .ifPresent(output::detail);
            }
        } else if (suffix.isBlank()) {
            Optional<String> noMainClassDetail = CommandPackageSupport.noMainClassDetail(result);
            if (noMainClassDetail.isPresent()) {
                output.detail(noMainClassDetail.orElseThrow());
            } else {
                output.detail("No Main-Class manifest entry; add [project].main to make the jar directly runnable.");
            }
        }
        if (suffix.isBlank()) {
            printPackageModeDetail(output, result);
        }
        output.detail("Wrote archive to " + result.jarPath());
        result.evidenceManifestPath().ifPresent(path ->
                output.detail("Wrote package evidence to " + path));
        for (PackageArtifact artifact : result.artifacts()) {
            output.detail(
                    "Wrote "
                            + artifact.classifier()
                            + " jar to "
                            + artifact.path());
        }
    }

    void print(PrintWriter out, PackageResult result, String suffix) {
        out.println(CommandPackageSupport.packageSummary(result) + suffix);
        if (result.hasMainClass()) {
            out.println(suffix.isBlank()
                    ? "Included Main-Class manifest entry"
                    : "Included Main-Class manifest entry" + suffix);
            if (suffix.isBlank()) {
                out.println("Run with: java -jar " + result.jarPath());
                CommandPackageSupport.runInstruction(result)
                        .ifPresent(out::println);
            }
        } else if (suffix.isBlank()) {
            Optional<String> noMainClassDetail = CommandPackageSupport.noMainClassDetail(result);
            if (noMainClassDetail.isPresent()) {
                out.println(noMainClassDetail.orElseThrow());
            } else {
                out.println("No Main-Class manifest entry; add [project].main to make the jar directly runnable.");
            }
        }
        if (suffix.isBlank()) {
            printPackageModeDetail(out, result);
        }
        out.println("Wrote archive to " + result.jarPath());
        result.evidenceManifestPath().ifPresent(path ->
                out.println("Wrote package evidence to " + path));
        for (PackageArtifact artifact : result.artifacts()) {
            out.println(
                    "Wrote "
                            + artifact.classifier()
                            + " jar to "
                            + artifact.path());
        }
    }

    private static void printPackageModeDetail(PrintWriter out, PackageResult result) {
        CommandPackageSupport.PackageModeDetail detail = CommandPackageSupport.packageModeDetail(result);
        out.println(detail.message());
        detail.secondaryMessage().ifPresent(out::println);
    }

    private static void printPackageModeDetail(CommandHumanOutput output, PackageResult result) {
        CommandPackageSupport.PackageModeDetail detail = CommandPackageSupport.packageModeDetail(result);
        output.detail(detail.message());
        detail.secondaryMessage().ifPresent(output::detail);
    }
}
