package com.zolt.arch;

import static com.zolt.arch.ComplexityBudgetSupport.describe;
import static com.zolt.arch.ComplexityBudgetSupport.overBudgetSources;
import static com.zolt.arch.ComplexityBudgetSupport.readAllowlist;
import static com.zolt.arch.ComplexityBudgetSupport.readBudgets;
import static com.zolt.arch.ComplexityBudgetSupport.scan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.arch.ComplexityBudgetSupport.AllowlistEntry;
import com.zolt.arch.ComplexityBudgetSupport.Budget;
import com.zolt.arch.ComplexityBudgetSupport.SourceComplexity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ComplexityBudgetTest {
    private static final Path BUDGETS =
            RepositoryPaths.appRoot().resolve("src/test/resources/com/zolt/arch/complexity-budgets.txt");
    private static final Path ALLOWLIST =
            RepositoryPaths.appRoot().resolve("src/test/resources/com/zolt/arch/complexity-allowlist.txt");
    private static final Path FOLLOW_UPS = RepositoryPaths.root().resolve("followUps");

    @Test
    void productionSourcesStayWithinComplexityBudgets() throws IOException {
        List<Budget> budgets = readBudgets(BUDGETS);
        Map<String, AllowlistEntry> allowlist = readAllowlist(ALLOWLIST);
        Map<String, SourceComplexity> overBudgetSources = overBudgetSources(budgets);
        List<String> violations = new ArrayList<>();

        for (SourceComplexity complexity : overBudgetSources.values()) {
            AllowlistEntry entry = allowlist.get(complexity.path());
            if (entry == null) {
                violations.add(complexity.path()
                        + " exceeds complexity budget: "
                        + complexity.describeMetrics()
                        + ". Extract behavior, introduce a value object, or add a planned exception.");
            } else if (complexity.imports() > entry.maxImports()
                    || complexity.publicMethods() > entry.maxPublicMethods()
                    || complexity.constructorParameters() > entry.maxConstructorParameters()
                    || complexity.nestedTypes() > entry.maxNestedTypes()) {
                violations.add(complexity.path()
                        + " grew beyond allowlisted complexity ["
                        + entry.followUp()
                        + "]: "
                        + complexity.describeMetrics());
            }
        }

        allowlist.keySet().stream()
                .filter(path -> !overBudgetSources.containsKey(path))
                .sorted()
                .forEach(path -> violations.add(path + " is no longer over the complexity budget; remove the allowlist entry"));

        assertTrue(
                violations.isEmpty(),
                () -> "Complexity budget violations:\n"
                        + describe(violations)
                        + "\nPrefer narrower collaborators, value objects, or a planned policy exception.");
    }

    @Test
    void complexityAllowlistEntriesReferenceExistingFollowUps() throws IOException {
        Set<String> followUpIds = ArchitectureFollowUpIds.read(FOLLOW_UPS);
        List<String> violations = readAllowlist(ALLOWLIST).values().stream()
                .map(AllowlistEntry::followUp)
                .distinct()
                .sorted()
                .filter(followUp -> !followUpIds.contains(followUp))
                .map(followUp -> followUp + " is referenced by the complexity allowlist but has no followUp file")
                .toList();

        assertTrue(
                violations.isEmpty(),
                () -> "Complexity allowlist followUp violations:\n" + describe(violations));
    }

    @Test
    void scannerCountsImportsPublicMethodsConstructorParametersAndNestedTypes(@TempDir Path tempDir)
            throws IOException {
        Path source = tempDir.resolve("src/main/java/com/example/FanOutOwner.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example;

                import java.nio.file.Path;
                import java.util.List;
                import java.util.Map;

                public final class FanOutOwner {
                    public static final String NAME = "fan-out";

                    public FanOutOwner(
                            Path root,
                            List<String> values,
                            Map<String, String> labels) {
                    }

                    public String render() {
                        return NAME;
                    }

                    public static int count() {
                        return 1;
                    }

                    private static final class Nested {
                    }
                }
                """);

        assertEquals(
                new SourceComplexity(
                        RepositoryPaths.displayPath(source),
                        3,
                        2,
                        3,
                        1),
                scan(source));
    }

    @Test
    void scannerAvoidsFalsePositiveFieldsAndConstructors(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("src/main/java/com/example/Value.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example;

                public final class Value {
                    public static final String NAME = "value";

                    public Value(String value) {
                    }

                    public String value() {
                        return NAME;
                    }
                }
                """);

        assertEquals(
                new SourceComplexity(RepositoryPaths.displayPath(source), 0, 1, 1, 0),
                scan(source));
    }
}
