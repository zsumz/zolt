package sh.zolt.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

final class TimingRecorderTest {
    @Test
    void disabledRecorderRunsActionWithoutRecordingEvents() {
        TimingRecorder recorder = new TimingRecorder(false, new FixedClock(0L, 10L));
        AtomicBoolean ran = new AtomicBoolean(false);

        recorder.measure("resolve graph", () -> ran.set(true));

        assertTrue(ran.get());
        assertTrue(recorder.events().isEmpty());
    }

    @Test
    void recordsNestedPhasesInCompletionOrderWithDepth() {
        TimingRecorder recorder = new TimingRecorder(true, new FixedClock(0L, 10L, 20L, 50L));

        String value = recorder.measure("outer", () -> recorder.measure("inner", () -> "done"));

        assertEquals("done", value);
        List<TimingEvent> events = recorder.events();
        assertEquals(2, events.size());
        assertEquals("inner", events.get(0).phase());
        assertEquals(10L, events.get(0).durationNanos());
        assertEquals(1, events.get(0).depth());
        assertEquals("outer", events.get(1).phase());
        assertEquals(50L, events.get(1).durationNanos());
        assertEquals(0, events.get(1).depth());
    }

    @Test
    void recordsAttributesForCompletedPhase() {
        TimingRecorder recorder = new TimingRecorder(true, new FixedClock(0L, 2_000_000L));

        Integer count = recorder.measure(
                "resolve graph",
                () -> 3,
                value -> Map.of("resolvedPackages", Integer.toString(value)));

        assertEquals(3, count);
        assertEquals(Map.of("resolvedPackages", "3"), recorder.events().getFirst().attributes());
    }

    @Test
    void recordsFailedStatusWhenPhaseThrows() {
        TimingRecorder recorder = new TimingRecorder(true, new FixedClock(0L, 2_000_000L));

        try {
            recorder.measure("config read", () -> {
                throw new IllegalStateException("broken");
            });
        } catch (IllegalStateException ignored) {
        }

        assertEquals(Map.of("status", "failed"), recorder.events().getFirst().attributes());
    }

    @Test
    void formatsTextWithSortedAttributesAndIndentation() {
        TimingEvent event = new TimingEvent(
                "resolve graph",
                2_000_000L,
                1,
                Map.of("resolvedPackages", "3", "downloadedArtifacts", "4"));

        String output = TimingFormatter.format(TimingFormat.TEXT, "resolve", Path.of("/repo"), List.of(event));

        assertEquals("""
                Timings for zolt resolve
                    resolve graph: 2 ms (downloadedArtifacts=4, resolvedPackages=3)
                """, output);
    }

    @Test
    void formatsJsonLinesWithEscapedStrings() {
        TimingEvent event = new TimingEvent(
                "phase \"one\"",
                2_000_000L,
                0,
                Map.of("status", "line\nbreak"));

        String output = TimingFormatter.format(TimingFormat.JSON, "ide model", Path.of("/repo"), List.of(event));

        assertTrue(output.startsWith("{\"command\":\"ide model\",\"projectRoot\":\"/repo\""));
        assertTrue(output.contains("\"phase\":\"phase \\\"one\\\"\""));
        assertTrue(output.contains("\"durationMillis\":2"));
        assertTrue(output.contains("\"durationNanos\":2000000"));
        assertTrue(output.contains("\"attributes\":{\"status\":\"line\\nbreak\"}"));
    }

    private static final class FixedClock implements LongSupplier {
        private final long[] values;
        private int index;

        private FixedClock(long... values) {
            this.values = values;
        }

        @Override
        public long getAsLong() {
            return values[index++];
        }
    }
}
