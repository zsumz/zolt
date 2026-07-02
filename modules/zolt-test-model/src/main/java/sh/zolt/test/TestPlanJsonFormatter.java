package sh.zolt.test;

import sh.zolt.project.ProjectConfig;
import sh.zolt.test.shard.TestShardBalancing;
import sh.zolt.test.shard.TestShardPlan;
import sh.zolt.test.shard.TestShardSpec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TestPlanJsonFormatter {
    public String json(
            ProjectConfig config,
            Path projectRoot,
            Optional<String> workspaceMember,
            TestSelection selection,
            TestSuitePlan plan,
            List<TestShardPlan> shards,
            Optional<Path> reportsDir) {
        Path root = projectRoot.toAbsolutePath().normalize();
        Optional<String> member = workspaceMember == null ? Optional.empty() : workspaceMember;
        Optional<Path> reports = reportsDir == null ? Optional.empty() : reportsDir;
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        intField(json, 1, "schemaVersion", 1, true);
        stringField(json, 1, "projectRoot", root.toString(), true);
        stringField(json, 1, "project", config.project().name(), true);
        optionalStringField(json, 1, "member", member, true);
        suite(json, plan);
        json.append(",\n");
        selection(json, selection == null ? TestSelection.empty() : selection);
        json.append(",\n");
        stringArrayField(json, 1, "entries", plan.entries().stream()
                .map(TestInventoryEntry::className)
                .toList(), true);
        stringArrayField(json, 1, "missingExplicitSelectors", plan.missingExplicitClassSelectors(), true);
        overlaps(json, plan.overlappingEntries());
        json.append(",\n");
        stringArrayField(json, 1, "unassignedEntries", plan.unassignedEntries(), true);
        balancing(json, shards);
        shards(json, root, member, selection == null ? TestSelection.empty() : selection, plan.suiteName(), shards, reports);
        json.append("\n}\n");
        return json.toString();
    }

    private static void suite(StringBuilder json, TestSuitePlan plan) {
        indent(json, 1).append("\"suite\": {\n");
        stringField(json, 2, "name", plan.suiteName(), true);
        booleanField(json, 2, "configured", plan.configuredSuite(), true);
        stringField(json, 2, "testOutput", plan.outputDirectory().toString(), true);
        intField(json, 2, "entryCount", plan.entries().size(), true);
        booleanField(json, 2, "empty", plan.empty(), true);
        stringArrayField(json, 2, "includeClassname", plan.includeClassname(), true);
        stringArrayField(json, 2, "excludeClassname", plan.excludeClassname(), true);
        stringArrayField(json, 2, "includeTag", plan.includeTag(), true);
        stringArrayField(json, 2, "excludeTag", plan.excludeTag(), false);
        indent(json, 1).append("}");
    }

    private static void selection(StringBuilder json, TestSelection selection) {
        indent(json, 1).append("\"selection\": {\n");
        stringArrayField(json, 2, "tests", testSelectors(selection), true);
        stringArrayField(json, 2, "patterns", selection.classNamePatterns(), true);
        stringArrayField(json, 2, "includeTags", selection.includedTags(), true);
        stringArrayField(json, 2, "excludeTags", selection.excludedTags(), false);
        indent(json, 1).append("}");
    }

    private static List<String> testSelectors(TestSelection selection) {
        List<String> selectors = new ArrayList<>(selection.classSelectors());
        selection.methodSelectors().stream()
                .map(method -> method.className() + "#" + method.methodName())
                .forEach(selectors::add);
        return List.copyOf(selectors);
    }

    private static void overlaps(StringBuilder json, Map<String, List<String>> overlaps) {
        indent(json, 1).append("\"overlappingEntries\": [");
        if (!overlaps.isEmpty()) {
            json.append('\n');
            int index = 0;
            for (Map.Entry<String, List<String>> entry : overlaps.entrySet()) {
                indent(json, 2).append("{\n");
                stringField(json, 3, "className", entry.getKey(), true);
                stringArrayField(json, 3, "suites", entry.getValue(), false);
                indent(json, 2).append("}");
                if (++index < overlaps.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void shards(
            StringBuilder json,
            Path projectRoot,
            Optional<String> workspaceMember,
            TestSelection selection,
            String suiteName,
            List<TestShardPlan> shards,
            Optional<Path> reportsDir) {
        indent(json, 1).append("\"shards\": [");
        if (!shards.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < shards.size(); index++) {
                TestShardPlan shard = shards.get(index);
                indent(json, 2).append("{\n");
                intField(json, 3, "index", shard.shard().index(), true);
                intField(json, 3, "total", shard.shard().total(), true);
                stringField(json, 3, "label", shard.shard().label(), true);
                intField(json, 3, "entryCount", shard.entries().size(), true);
                shard.balancing().ifPresent(value -> longField(json, 3, "estimatedCostMillis", shard.estimatedCostMillis(), true));
                booleanField(json, 3, "empty", shard.empty(), true);
                stringField(json, 3, "manifest", shard.projectRelativeManifestPath(projectRoot).toString(), true);
                stringArrayField(json, 3, "entries", shard.entries().stream()
                        .map(TestInventoryEntry::className)
                        .toList(), true);
                commandArguments(json, projectRoot, workspaceMember, selection, suiteName, shard.shard(), reportsDir);
                json.append('\n');
                indent(json, 2).append("}");
                if (index + 1 < shards.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void balancing(StringBuilder json, List<TestShardPlan> shards) {
        Optional<TestShardBalancing> balancing = shards.stream()
                .flatMap(shard -> shard.balancing().stream())
                .findFirst();
        if (balancing.isEmpty()) {
            return;
        }
        TestShardBalancing value = balancing.orElseThrow();
        indent(json, 1).append("\"balancing\": {\n");
        stringField(json, 2, "mode", value.mode(), true);
        optionalStringField(json, 2, "profileSource", value.profileSource().map(Path::toString), true);
        stringArrayField(json, 2, "missingHistoryEntries", value.missingHistoryEntries(), true);
        stringArrayField(json, 2, "unmatchedHistoryEntries", value.unmatchedHistoryEntries(), true);
        stringArrayField(json, 2, "diagnostics", value.diagnostics(), false);
        indent(json, 1).append("},\n");
    }

    private static void commandArguments(
            StringBuilder json,
            Path projectRoot,
            Optional<String> workspaceMember,
            TestSelection selection,
            String suiteName,
            TestShardSpec shard,
            Optional<Path> reportsDir) {
        indent(json, 3).append("\"command\": {\n");
        stringArrayField(
                json,
                4,
                "arguments",
                shardCommandArguments(projectRoot, workspaceMember, selection, suiteName, shard, reportsDir),
                false);
        indent(json, 3).append("}");
    }

    private static List<String> shardCommandArguments(
            Path projectRoot,
            Optional<String> workspaceMember,
            TestSelection selection,
            String suiteName,
            TestShardSpec shard,
            Optional<Path> reportsDir) {
        List<String> arguments = new ArrayList<>();
        arguments.add("test");
        if (workspaceMember.isPresent()) {
            arguments.add("--workspace");
            arguments.add("--member");
            arguments.add(workspaceMember.orElseThrow());
        } else {
            arguments.add("--directory");
            arguments.add(projectRoot.toString());
        }
        arguments.add("--suite");
        arguments.add(suiteName);
        addSelectionArguments(arguments, selection);
        arguments.add("--shard");
        arguments.add(shard.label());
        reportsDir.ifPresent(path -> {
            arguments.add("--reports-dir");
            arguments.add(path.toString());
        });
        return List.copyOf(arguments);
    }

    private static void addSelectionArguments(List<String> arguments, TestSelection selection) {
        for (String selector : selection.classSelectors()) {
            arguments.add("--test");
            arguments.add(selector);
        }
        for (TestSelection.MethodSelector method : selection.methodSelectors()) {
            arguments.add("--test");
            arguments.add(method.className() + "#" + method.methodName());
        }
        for (String pattern : selection.classNamePatterns()) {
            arguments.add("--tests");
            arguments.add(pattern);
        }
        for (String tag : selection.includedTags()) {
            arguments.add("--include-tag");
            arguments.add(tag);
        }
        for (String tag : selection.excludedTags()) {
            arguments.add("--exclude-tag");
            arguments.add(tag);
        }
    }

    private static void intField(StringBuilder json, int level, String name, int value, boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ").append(value);
        comma(json, trailingComma);
    }

    private static void longField(StringBuilder json, int level, String name, long value, boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ").append(value);
        comma(json, trailingComma);
    }

    private static void booleanField(StringBuilder json, int level, String name, boolean value, boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ").append(value);
        comma(json, trailingComma);
    }

    private static void optionalStringField(
            StringBuilder json,
            int level,
            String name,
            Optional<String> value,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ");
        if (value.isPresent()) {
            string(json, value.orElseThrow());
        } else {
            json.append("null");
        }
        comma(json, trailingComma);
    }

    private static void stringField(StringBuilder json, int level, String name, String value, boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ");
        string(json, value);
        comma(json, trailingComma);
    }

    private static void stringArrayField(
            StringBuilder json,
            int level,
            String name,
            List<String> values,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": [");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            string(json, values.get(index));
        }
        json.append("]");
        comma(json, trailingComma);
    }

    private static void comma(StringBuilder json, boolean trailingComma) {
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void string(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
    }
}
