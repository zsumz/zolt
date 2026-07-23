package sh.zolt.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.UpdateClass;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class OutdatedTextRendererTest {
    private final OutdatedTextRenderer renderer = new OutdatedTextRenderer();

    @Test
    void rendersUpdateRowWithClassLatestAndSource() {
        OutdatedCandidates candidates = new OutdatedCandidates(
                Optional.of("1.0.1"),
                Optional.of("1.1.0"),
                Optional.of("2.0.0"),
                Optional.of("1.1.0"),
                Optional.of(UpdateClass.MINOR),
                Optional.of("2.0.0"),
                Optional.of(UpdateClass.MAJOR));
        OutdatedReport report = report(new OutdatedEntry(
                OutdatedSurface.DEPENDENCY,
                "com.example:lib",
                "[dependencies]",
                "1.0.0",
                OutdatedStatus.UPDATE_AVAILABLE,
                candidates,
                Optional.of("central"),
                List.of(),
                List.of(),
                List.of()));

        assertEquals(
                "demo\n  com.example:lib  1.0.0  -> 1.1.0 (minor)  latest 2.0.0 (major)  central\n",
                renderer.render(report));
    }

    @Test
    void rendersAliasGovernsAndUnknownNotes() {
        OutdatedReport report = report(
                new OutdatedEntry(
                        OutdatedSurface.VERSION_ALIAS,
                        "guava",
                        "[versions]",
                        "33.4.0-jre",
                        OutdatedStatus.UPDATE_AVAILABLE,
                        new OutdatedCandidates(
                                Optional.of("33.4.8-jre"),
                                Optional.of("33.4.8-jre"),
                                Optional.of("33.4.8-jre"),
                                Optional.of("33.4.8-jre"),
                                Optional.of(UpdateClass.PATCH),
                                Optional.of("33.4.8-jre"),
                                Optional.of(UpdateClass.PATCH)),
                        Optional.of("central"),
                        List.of("[dependencies].com.google.guava:guava"),
                        List.of(),
                        List.of()),
                new OutdatedEntry(
                        OutdatedSurface.DEPENDENCY,
                        "com.example:lib",
                        "[dependencies]",
                        "9.9.9",
                        OutdatedStatus.UNKNOWN,
                        OutdatedCandidates.none(),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        List.of("Offline: no cached version listing.")));

        String text = renderer.render(report);
        assertTrue(text.contains("governs [dependencies].com.google.guava:guava"));
        assertTrue(text.contains("unknown"));
        assertTrue(text.contains("note: Offline: no cached version listing."));
    }

    private static OutdatedReport report(OutdatedEntry... entries) {
        return new OutdatedReport(List.of(new OutdatedScopeReport("demo", List.of(entries))), List.of());
    }
}
