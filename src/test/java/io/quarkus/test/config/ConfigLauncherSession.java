package io.quarkus.test.config;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.platform.launcher.LauncherSession;

public final class ConfigLauncherSession {
    private static final AtomicBoolean OPENED = new AtomicBoolean(false);

    public void launcherSessionOpened(LauncherSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        OPENED.set(true);
    }

    public static boolean opened() {
        return OPENED.get();
    }

    public static void reset() {
        OPENED.set(false);
    }
}
