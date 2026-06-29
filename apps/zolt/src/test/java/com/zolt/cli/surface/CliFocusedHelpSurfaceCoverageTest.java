package com.zolt.cli.surface;

import static com.zolt.cli.CliTestSupport.newCommandLine;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.commandPaths;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class CliFocusedHelpSurfaceCoverageTest {
    private static final Set<String> FOCUSED_HELP_SURFACE_PATHS = Set.of(
            "zolt",
            "zolt add",
            "zolt aliases",
            "zolt build",
            "zolt check",
            "zolt classpath",
            "zolt clean",
            "zolt config",
            "zolt config show",
            "zolt conflicts",
            "zolt coverage",
            "zolt doctor",
            "zolt explain",
            "zolt help",
            "zolt ide",
            "zolt ide model",
            "zolt init",
            "zolt integration-test",
            "zolt native",
            "zolt native-smoke",
            "zolt package",
            "zolt platform",
            "zolt platform add",
            "zolt platform remove",
            "zolt plan",
            "zolt policy",
            "zolt publish",
            "zolt quarkus",
            "zolt quarkus plan",
            "zolt quarkus test-plan",
            "zolt release-archive",
            "zolt release-verify",
            "zolt remove",
            "zolt resolve",
            "zolt run",
            "zolt run-package",
            "zolt self-check",
            "zolt self-parity",
            "zolt task",
            "zolt tasks",
            "zolt test",
            "zolt test plan",
            "zolt tree",
            "zolt update",
            "zolt version",
            "zolt version remove",
            "zolt version set",
            "zolt why");

    @Test
    void allRegisteredCommandPathsHaveFocusedHelpSurfaceOwnership() {
        Set<String> registeredPaths = commandPaths(newCommandLine()).stream()
                .map(CliFocusedHelpSurfaceCoverageTest::commandPath)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(FOCUSED_HELP_SURFACE_PATHS, registeredPaths);
    }

    private static String commandPath(List<String> path) {
        return path.isEmpty() ? "zolt" : "zolt " + String.join(" ", path);
    }
}
