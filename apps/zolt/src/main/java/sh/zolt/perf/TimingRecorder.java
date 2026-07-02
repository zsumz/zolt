package sh.zolt.perf;

import sh.zolt.ide.IdeTimingRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class TimingRecorder implements IdeTimingRecorder {
    private final boolean enabled;
    private final LongSupplier nanoTime;
    private final List<TimingEvent> events = new ArrayList<>();
    private int depth;

    public TimingRecorder(boolean enabled) {
        this(enabled, System::nanoTime);
    }

    TimingRecorder(boolean enabled, LongSupplier nanoTime) {
        this.enabled = enabled;
        this.nanoTime = nanoTime;
    }

    public boolean enabled() {
        return enabled;
    }

    public List<TimingEvent> events() {
        return List.copyOf(events);
    }

    public void measure(String phase, Runnable action) {
        measure(phase, () -> {
            action.run();
            return null;
        });
    }

    public <T> T measure(String phase, Supplier<T> action) {
        return measure(phase, action, ignored -> Map.of());
    }

    public <T> T measure(String phase, Supplier<T> action, Function<T, Map<String, String>> attributes) {
        if (!enabled) {
            return action.get();
        }
        int eventDepth = depth;
        depth++;
        long started = nanoTime.getAsLong();
        T result = null;
        boolean completed = false;
        try {
            result = action.get();
            completed = true;
            return result;
        } finally {
            long duration = nanoTime.getAsLong() - started;
            depth--;
            Map<String, String> eventAttributes = completed ? attributes.apply(result) : Map.of("status", "failed");
            events.add(new TimingEvent(phase, Math.max(0L, duration), eventDepth, eventAttributes));
        }
    }
}
