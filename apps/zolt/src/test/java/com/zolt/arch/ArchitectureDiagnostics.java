package com.zolt.arch;

import java.util.List;

final class ArchitectureDiagnostics {
    private ArchitectureDiagnostics() {}

    static String describe(List<String> values) {
        StringBuilder description = new StringBuilder();
        for (String value : values) {
            description.append("- ").append(value).append('\n');
        }
        return description.toString();
    }
}
