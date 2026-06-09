package com.zolt.perf;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

public final class TimingFormatter {
    private TimingFormatter() {
    }

    public static String format(TimingFormat format, String command, Path projectRoot, Iterable<TimingEvent> events) {
        return switch (format) {
            case TEXT -> text(command, events);
            case JSON -> json(command, projectRoot, events);
        };
    }

    private static String text(String command, Iterable<TimingEvent> events) {
        StringBuilder output = new StringBuilder();
        output.append("Timings for zolt ").append(command).append(System.lineSeparator());
        for (TimingEvent event : events) {
            output.append("  ");
            output.append("  ".repeat(event.depth()));
            output.append(event.phase())
                    .append(": ")
                    .append(event.durationMillis())
                    .append(" ms");
            if (!event.attributes().isEmpty()) {
                output.append(" (");
                appendAttributes(output, event.attributes());
                output.append(')');
            }
            output.append(System.lineSeparator());
        }
        return output.toString();
    }

    private static String json(String command, Path projectRoot, Iterable<TimingEvent> events) {
        StringBuilder output = new StringBuilder();
        String root = projectRoot.toAbsolutePath().normalize().toString();
        for (TimingEvent event : events) {
            output.append('{');
            jsonField(output, "command", command);
            output.append(',');
            jsonField(output, "projectRoot", root);
            output.append(',');
            jsonField(output, "phase", event.phase());
            output.append(",\"durationMillis\":").append(event.durationMillis());
            output.append(",\"durationNanos\":").append(event.durationNanos());
            output.append(",\"depth\":").append(event.depth());
            output.append(",\"attributes\":{");
            boolean first = true;
            for (Map.Entry<String, String> attribute : event.attributes().entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .toList()) {
                if (!first) {
                    output.append(',');
                }
                jsonField(output, attribute.getKey(), attribute.getValue());
                first = false;
            }
            output.append("}}").append(System.lineSeparator());
        }
        return output.toString();
    }

    private static void appendAttributes(StringBuilder output, Map<String, String> attributes) {
        boolean first = true;
        for (Map.Entry<String, String> attribute : attributes.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .toList()) {
            if (!first) {
                output.append(", ");
            }
            output.append(attribute.getKey()).append('=').append(attribute.getValue());
            first = false;
        }
    }

    private static void jsonField(StringBuilder output, String name, String value) {
        output.append('"').append(escapeJson(name)).append("\":\"").append(escapeJson(value)).append('"');
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
