package com.zolt.test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record TestShardBalancing(
        String mode,
        Optional<Path> profileSource,
        List<String> missingHistoryEntries,
        List<String> unmatchedHistoryEntries,
        List<String> diagnostics) {
    public static final String ROUND_ROBIN = "round-robin";
    public static final String PROFILE_HISTORY = "profile-history";

    public TestShardBalancing {
        mode = mode == null || mode.isBlank() ? ROUND_ROBIN : mode;
        profileSource = profileSource == null ? Optional.empty() : profileSource;
        missingHistoryEntries = missingHistoryEntries == null ? List.of() : List.copyOf(missingHistoryEntries);
        unmatchedHistoryEntries = unmatchedHistoryEntries == null ? List.of() : List.copyOf(unmatchedHistoryEntries);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean profileDriven() {
        return PROFILE_HISTORY.equals(mode);
    }
}
