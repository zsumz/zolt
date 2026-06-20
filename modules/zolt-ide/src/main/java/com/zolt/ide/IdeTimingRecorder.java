package com.zolt.ide;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public interface IdeTimingRecorder {
    static IdeTimingRecorder disabled() {
        return Disabled.INSTANCE;
    }

    void measure(String phase, Runnable action);

    <T> T measure(String phase, Supplier<T> action);

    <T> T measure(String phase, Supplier<T> action, Function<T, Map<String, String>> attributes);

    final class Disabled implements IdeTimingRecorder {
        private static final Disabled INSTANCE = new Disabled();

        private Disabled() {
        }

        @Override
        public void measure(String phase, Runnable action) {
            action.run();
        }

        @Override
        public <T> T measure(String phase, Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T measure(String phase, Supplier<T> action, Function<T, Map<String, String>> attributes) {
            return action.get();
        }
    }
}
