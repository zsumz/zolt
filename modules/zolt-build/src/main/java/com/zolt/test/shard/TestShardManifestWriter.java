package com.zolt.test.shard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

public final class TestShardManifestWriter {
    public void write(TestShardPlan plan) {
        try {
            Files.createDirectories(plan.manifestPath().getParent());
            Files.writeString(plan.manifestPath(), json(plan));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write test shard manifest to " + plan.manifestPath() + ".", exception);
        }
    }

    private static String json(TestShardPlan plan) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        appendField(json, "version", "1", false);
        appendField(json, "suite", quote(plan.suiteName()), false);
        json.append("  \"shard\": {\n");
        json.append("    \"index\": ").append(plan.shard().index()).append(",\n");
        json.append("    \"total\": ").append(plan.shard().total()).append("\n");
        json.append("  },\n");
        appendField(json, "inventoryFingerprint", quote(plan.inventoryFingerprint()), false);
        appendField(json, "inventoryEntries", Integer.toString(plan.inventoryEntries().size()), false);
        appendField(json, "selectedEntries", Integer.toString(plan.entries().size()), false);
        appendField(json, "empty", Boolean.toString(plan.empty()), false);
        json.append("  \"entries\": [\n");
        for (int index = 0; index < plan.entries().size(); index++) {
            json.append("    ").append(quote(plan.entries().get(index).className()));
            if (index + 1 < plan.entries().size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void appendField(StringBuilder json, String name, String value, boolean last) {
        json.append("  \"").append(name).append("\": ").append(value);
        if (!last) {
            json.append(",");
        }
        json.append("\n");
    }

    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> quoted.append("\\\\");
                case '"' -> quoted.append("\\\"");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> quoted.append(character);
            }
        }
        return quoted.append('"').toString();
    }
}
