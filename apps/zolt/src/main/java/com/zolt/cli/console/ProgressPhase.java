package com.zolt.cli.console;

/**
 * A single phase of work returned by {@link ProgressWriter#phase(String)}.
 *
 * <p>On the interactive (sleek) path a phase shows an animated spinner followed by {@code <name>} on
 * a live stderr line, then commits {@code ✔ <name>} to scrollback on {@link #done()} or {@code ✗ <name>}
 * on {@link #fail()}, restoring the terminal cursor.
 *
 * <p>On the fallback path (non-TTY / {@code --progress=never} / CI / PARSEABLE / progress disabled)
 * the phase does no animation and emits nothing itself — the caller's existing
 * {@link ProgressWriter#start(String)} / {@link ProgressWriter#result(String)} lines remain the
 * append-only output, byte-for-byte identical to today. {@link #done()} / {@link #fail()} are no-ops.
 *
 * <p>A phase is single-use: after {@link #done()} or {@link #fail()} further calls are no-ops.
 */
public final class ProgressPhase {
    private static final String SUCCESS_GLYPH = "✔";
    private static final String FAILURE_GLYPH = "✗";

    private final String name;
    private final LiveRegion region;
    private final SpinnerTicker ticker;
    private final ConsoleStyle style;
    private final boolean animated;
    private boolean finished;

    private ProgressPhase(String name, LiveRegion region, ConsoleStyle style, boolean animated) {
        this.name = name;
        this.region = region;
        this.style = style;
        this.animated = animated;
        this.ticker = animated
                ? new SpinnerTicker(index -> {
                    region.render(frame(index));
                    return null;
                })
                : null;
    }

    static ProgressPhase animated(String name, LiveRegion region, ConsoleStyle style) {
        ProgressPhase phase = new ProgressPhase(name, region, style, true);
        region.start();
        phase.ticker.start();
        return phase;
    }

    static ProgressPhase inert(String name) {
        return new ProgressPhase(name, null, ConsoleStyle.disabled(), false);
    }

    /** Commit a {@code ✔ <name>} line to scrollback and restore the terminal. Idempotent. */
    public void done() {
        finish(style.success(SUCCESS_GLYPH) + " " + name);
    }

    /** Commit a {@code ✗ <name>} line to scrollback and restore the terminal. Idempotent. */
    public void fail() {
        finish(style.error(FAILURE_GLYPH) + " " + name);
    }

    private void finish(String committedLine) {
        if (finished) {
            return;
        }
        finished = true;
        if (!animated) {
            return;
        }
        ticker.stop();
        region.commit(committedLine);
        region.stop();
    }

    private String frame(int index) {
        return style.work(String.valueOf(SpinnerTicker.frame(index))) + " " + name;
    }
}
