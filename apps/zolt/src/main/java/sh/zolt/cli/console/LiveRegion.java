package sh.zolt.cli.console;

import java.io.PrintWriter;

/**
 * A single-writer, in-place progress region on a {@link PrintWriter} (stderr).
 *
 * <p>The region owns one live line. {@link #render(String)} rewrites that line with a carriage
 * return and clear-to-end-of-line so successive frames redraw in place; {@link #commit(String)}
 * promotes a finished line into scrollback with a plain {@code println}. While the region is active
 * the terminal cursor is hidden and is always restored on {@link #stop()}, on JVM exit (a shutdown
 * hook), and on any exception (callers use try/finally).
 *
 * <p>All writes funnel through {@code this} monitor so a background ticker frame never interleaves
 * with a committed line or with the summary printer.
 */
public final class LiveRegion {
    private static final String CR = "\r";
    private static final String CLEAR_TO_EOL = "[K";
    private static final String HIDE_CURSOR = "[?25l";
    private static final String SHOW_CURSOR = "[?25h";

    private final PrintWriter err;
    private boolean active;
    private boolean cursorHidden;
    private boolean dirty;
    private Thread shutdownHook;

    public LiveRegion(PrintWriter err) {
        this.err = err;
    }

    /**
     * Begin an in-place region: hide the cursor and register a shutdown hook that restores it if the
     * JVM exits (e.g. SIGINT) before {@link #stop()} runs. Idempotent.
     */
    public synchronized void start() {
        if (active) {
            return;
        }
        active = true;
        dirty = false;
        hideCursor();
        installShutdownHook();
    }

    /** Redraw the live line in place. No-op unless the region is active. */
    public synchronized void render(String line) {
        if (!active) {
            return;
        }
        err.print(CR);
        err.print(line);
        err.print(CLEAR_TO_EOL);
        err.flush();
        dirty = true;
    }

    /**
     * Clear the live line and promote {@code line} into scrollback as a plain, permanent line. The
     * region stays active (cursor stays hidden) so a subsequent phase can reuse it.
     */
    public synchronized void commit(String line) {
        if (!active) {
            err.println(line);
            err.flush();
            return;
        }
        clearLine();
        err.println(line);
        err.flush();
        dirty = false;
    }

    /**
     * Stop the region: clear any pending live line, restore the cursor, and drop the shutdown hook.
     * Idempotent and safe to call from a finally block.
     */
    public synchronized void stop() {
        if (!active) {
            return;
        }
        clearLine();
        showCursor();
        removeShutdownHook();
        active = false;
    }

    private void clearLine() {
        if (dirty) {
            err.print(CR);
            err.print(CLEAR_TO_EOL);
            err.flush();
            dirty = false;
        }
    }

    private void hideCursor() {
        if (!cursorHidden) {
            err.print(HIDE_CURSOR);
            err.flush();
            cursorHidden = true;
        }
    }

    private void showCursor() {
        if (cursorHidden) {
            err.print(SHOW_CURSOR);
            err.flush();
            cursorHidden = false;
        }
    }

    private void installShutdownHook() {
        if (shutdownHook != null) {
            return;
        }
        Thread hook = new Thread(this::restoreCursorOnExit, "zolt-live-region-cursor-restore");
        try {
            Runtime.getRuntime().addShutdownHook(hook);
            shutdownHook = hook;
        } catch (IllegalStateException alreadyShuttingDown) {
            // JVM is already shutting down; the try/finally in the caller covers cursor restoration.
        }
    }

    private void removeShutdownHook() {
        if (shutdownHook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException shuttingDown) {
            // Cannot remove during shutdown; the hook will simply run and restore the cursor.
        }
        shutdownHook = null;
    }

    private synchronized void restoreCursorOnExit() {
        showCursor();
    }
}
