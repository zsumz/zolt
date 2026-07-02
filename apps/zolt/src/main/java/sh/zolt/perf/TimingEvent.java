package sh.zolt.perf;

import java.util.Map;

public record TimingEvent(
        String phase,
        long durationNanos,
        int depth,
        Map<String, String> attributes) {
    public TimingEvent {
        if (phase == null || phase.isBlank()) {
            throw new IllegalArgumentException("Timing phase is required.");
        }
        if (durationNanos < 0) {
            throw new IllegalArgumentException("Timing duration must not be negative.");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public long durationMillis() {
        return durationNanos / 1_000_000L;
    }
}
