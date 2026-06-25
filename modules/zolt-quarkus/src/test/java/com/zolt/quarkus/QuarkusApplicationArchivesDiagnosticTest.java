package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class QuarkusApplicationArchivesDiagnosticTest {
    @Test
    void reportsMissingApplicationArchivesBuildItem() {
        assertEquals("<missing>", QuarkusApplicationArchivesDiagnostic.formatArchives(null, Path.of("target/test-classes")));
        assertEquals(
                "<missing>",
                QuarkusApplicationArchivesDiagnostic.formatSelectedClassArchives(null, List.of("com.example.Test")));
    }

    @Test
    void reportsTestOutputArchivePresenceAndSelectedClassOwnership() {
        Path testOutput = Path.of("target/test-classes").toAbsolutePath().normalize();
        FakeArchive testArchive = new FakeArchive(testOutput);
        FakeApplicationArchives archives = new FakeApplicationArchives(
                List.of(new FakeArchive(Path.of("target/classes")), testArchive),
                Map.of("com.example.ProfiledTest", testArchive));

        assertEquals(
                "count=2,testOutputArchive=true",
                QuarkusApplicationArchivesDiagnostic.formatArchives(archives, testOutput));
        String selectedClasses = QuarkusApplicationArchivesDiagnostic.formatSelectedClassArchives(
                archives,
                List.of("com.example.ProfiledTest", "com.example.MissingTest"));

        assertTrue(selectedClasses.contains("com.example.ProfiledTest[archive=" + testOutput), selectedClasses);
        assertTrue(selectedClasses.contains("com.example.MissingTest[archive=<missing>]"), selectedClasses);
    }

    static final class FakeApplicationArchives {
        private final Collection<FakeArchive> archives;
        private final Map<String, FakeArchive> containingArchives;

        FakeApplicationArchives(
                Collection<FakeArchive> archives,
                Map<String, FakeArchive> containingArchives) {
            this.archives = archives;
            this.containingArchives = containingArchives;
        }

        public Collection<FakeArchive> getAllApplicationArchives() {
            return archives;
        }

        public FakeArchive containingArchive(String className) {
            return containingArchives.get(className);
        }
    }

    static final class FakeArchive {
        private final Path root;

        FakeArchive(Path root) {
            this.root = root.toAbsolutePath().normalize();
        }

        public Iterable<Path> getRootDirectories() {
            return List.of(root);
        }
    }
}
