package com.zolt.cli.command;

import com.zolt.build.PackageException;
import com.zolt.build.PackageResult;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import java.util.Optional;

final class CommandPackageSupport {
    private CommandPackageSupport() {
    }

    static Optional<PackageMode> packageModeOverride(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(PackageMode.fromConfigValue(value).orElseThrow(() -> new PackageException(
                "Unsupported package mode `"
                        + value
                        + "`. Supported package modes are: "
                        + PackageMode.supportedValues()
                        + ".")));
    }

    static ProjectConfig withPackageModeOverride(
            ProjectConfig config,
            Optional<PackageMode> packageModeOverride) {
        return packageModeOverride
                .map(mode -> config.withPackageSettings(new PackageSettings(mode)))
                .orElse(config);
    }

    static PlanOutputFormat planOutputFormat(String value) {
        String normalized = value == null || value.isBlank() ? "text" : value.trim().toLowerCase();
        return switch (normalized) {
            case "text" -> PlanOutputFormat.TEXT;
            case "json" -> PlanOutputFormat.JSON;
            default -> throw new PackageException("Unsupported package plan format `" + value + "`. Use text or json.");
        };
    }

    static String packageSummary(PackageResult result) {
        if (result.mode() == PackageMode.QUARKUS) {
            return "Packaged Quarkus fast-jar layout with " + result.entryCount() + " files";
        }
        String extension = (result.mode() == PackageMode.WAR || result.mode() == PackageMode.SPRING_BOOT_WAR)
                ? "war"
                : "jar";
        return "Packaged "
                + result.entryCount()
                + " compiled files as "
                + result.mode().configValue()
                + " "
                + extension;
    }

    enum PlanOutputFormat {
        TEXT,
        JSON
    }
}
