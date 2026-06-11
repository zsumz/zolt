package com.zolt.explain;

import java.util.List;

public record MigrationReadinessConcern(
        String name,
        String status,
        List<MigrationReadinessFinding> findings) {
    public MigrationReadinessConcern {
        findings = List.copyOf(findings);
    }
}
