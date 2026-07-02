package sh.zolt.cli.console;

import java.util.function.IntFunction;

/**
 * A low-frame-rate daemon thread that advances a spinner and redraws the current live line so a
 * phase animates continuously even while the underlying work emits no events (e.g. a slow download).
 *
 * <p>Native-image safe: a plain daemon {@link Thread} with no reflection. The ticker never writes
 * directly; it asks the supplied {@code frameRenderer} to draw frame {@code n}, and that renderer
 * funnels through {@link LiveRegion}'s monitor, so ticker frames never split a committed line.
 */
public final class SpinnerTicker {
    static final char[] FRAMES = {'⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'};
    private static final long FRAME_INTERVAL_MILLIS = 90L;

    private final IntFunction<Void> frameRenderer;
    private final long frameIntervalMillis;
    private Thread thread;

    public SpinnerTicker(IntFunction<Void> frameRenderer) {
        this(frameRenderer, FRAME_INTERVAL_MILLIS);
    }

    SpinnerTicker(IntFunction<Void> frameRenderer, long frameIntervalMillis) {
        this.frameRenderer = frameRenderer;
        this.frameIntervalMillis = frameIntervalMillis;
    }

    /** The glyph for spinner frame {@code index}. */
    public static char frame(int index) {
        return FRAMES[Math.floorMod(index, FRAMES.length)];
    }

    /** Start advancing frames on a daemon thread. Renders the initial frame immediately. */
    public synchronized void start() {
        if (thread != null) {
            return;
        }
        frameRenderer.apply(0);
        Thread worker = new Thread(this::run, "zolt-spinner-ticker");
        worker.setDaemon(true);
        thread = worker;
        worker.start();
    }

    /** Stop the ticker and wait for the daemon thread to exit. Idempotent. */
    public synchronized void stop() {
        Thread worker = thread;
        if (worker == null) {
            return;
        }
        thread = null;
        worker.interrupt();
        try {
            worker.join(500L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        int index = 1;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(frameIntervalMillis);
            } catch (InterruptedException interrupted) {
                return;
            }
            synchronized (this) {
                if (thread != Thread.currentThread()) {
                    return;
                }
            }
            frameRenderer.apply(index++);
        }
    }
}
