package com.zolt.conflict;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.dependency.PackageId;
import com.zolt.dependency.ConflictSelectionReason;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DependencyConflictFormatterTest {
    private final DependencyConflictFormatter formatter = new DependencyConflictFormatter();

    @Test
    void formatsConflictsDeterministically() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(),
                List.of(
                        new LockConflict(
                                new PackageId("org.slf4j", "slf4j-api"),
                                "2.0.16",
                                List.of("2.0.16", "1.7.36"),
                                ConflictSelectionReason.DIRECT_DEPENDENCY),
                        new LockConflict(
                                new PackageId("com.google.guava", "guava"),
                                "33.4.0-jre",
                                List.of("32.1.3-jre", "33.4.0-jre"),
                                ConflictSelectionReason.NEWEST_VERSION)));

        String output = formatter.format(lockfile);

        assertEquals("""
                Dependency conflicts:
                - com.google.guava:guava
                  selected: 33.4.0-jre
                  requested: 32.1.3-jre, 33.4.0-jre
                  reason: newest version wins
                - org.slf4j:slf4j-api
                  selected: 2.0.16
                  requested: 1.7.36, 2.0.16
                  reason: direct dependency wins
                """, output);
    }

    @Test
    void reportsNoConflictsSuccessfully() {
        String output = formatter.format(new ZoltLockfile(ZoltLockfile.CURRENT_VERSION, List.of(), List.of()));

        assertEquals("No dependency conflicts found.\n", output);
    }
}
