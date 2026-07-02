package sh.zolt.cli.console;

import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.resolve.progress.ArtifactProgressListener;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressWriter {
    private final PrintWriter err;
    private final ProgressPolicy policy;
    private final ConsoleStyle style;
    private final ProgressOutputContract outputContract;
    private LiveRegion liveRegion;

    public ProgressWriter(
            PrintWriter err,
            ProgressPolicy policy,
            ConsoleStyle style,
            ProgressOutputContract outputContract) {
        this.err = err;
        this.policy = policy;
        this.style = style;
        this.outputContract = outputContract;
    }

    public static ProgressWriter disabled(PrintWriter err) {
        return new ProgressWriter(
                err,
                new ProgressPolicy(ProgressMode.NEVER, false, java.util.Map.of()),
                ConsoleStyle.disabled(),
                ProgressOutputContract.HUMAN);
    }

    public boolean enabled() {
        return policy.enabled(outputContract);
    }

    /**
     * Whether the sleek, animated live-region path is active: progress is enabled AND stderr is a
     * genuine interactive TTY. The animated path (spinner, {@code \r}, cursor hide/show) is additive
     * and TTY-only; when this is false, {@link #phase(String)} falls back to today's append-only lines.
     */
    public boolean animated() {
        return enabled() && policy.interactiveStderr();
    }

    public ArtifactProgressListener artifactProgressListener() {
        if (!animated()) {
            return ArtifactProgressListener.NOOP;
        }
        return new LiveArtifactProgressListener(liveRegion(), style);
    }

    /**
     * Begin a named phase. On an interactive TTY this shows an animated spinner + {@code <name>} on a
     * live stderr line that flips to {@code ✔ <name>} (or {@code ✗ <name>}) when the returned handle is
     * finished. On the fallback path it emits today's {@code <name>...} start line and returns an inert
     * handle, so append-only output stays byte-for-byte identical.
     */
    public ProgressPhase phase(String name) {
        if (animated()) {
            return ProgressPhase.animated(name, liveRegion(), style);
        }
        start(name);
        return ProgressPhase.inert(name);
    }

    private LiveRegion liveRegion() {
        if (liveRegion == null) {
            liveRegion = new LiveRegion(err);
        }
        return liveRegion;
    }

    public void start(String message) {
        line(styledLead(message, LeadStyle.WORK) + "...");
    }

    public void step(String message) {
        line(styledLead(message, LeadStyle.WORK));
    }

    public void heartbeat(String message) {
        line(styledLead(message, LeadStyle.WORK));
    }

    public void result(String message) {
        line(styledLead(message, LeadStyle.SUCCESS));
    }

    private void line(String message) {
        if (!enabled()) {
            return;
        }
        err.println(message);
        err.flush();
    }

    private String styledLead(String message, LeadStyle leadStyle) {
        int separator = message.indexOf(' ');
        if (separator < 0) {
            return style(leadStyle, message);
        }
        return style(leadStyle, message.substring(0, separator)) + message.substring(separator);
    }

    private String style(LeadStyle leadStyle, String text) {
        return switch (leadStyle) {
            case WORK -> style.work(text);
            case SUCCESS -> style.success(text);
        };
    }

    private enum LeadStyle {
        WORK,
        SUCCESS
    }

    private static final class LiveArtifactProgressListener implements ArtifactProgressListener {
        private static final String SPINNER_GLYPH = "⠋";
        private static final String SUCCESS_GLYPH = "✔";
        private static final String FAILURE_GLYPH = "✗";
        private static final int BAR_WIDTH = 10;
        private static final long LARGE_ARTIFACT_BYTES = 64L * 1024L;
        private static final long BYTE_RENDER_MIN_INTERVAL_NANOS = 90_000_000L;

        private final LiveRegion region;
        private final ConsoleStyle style;
        private final Map<ArtifactDescriptor, Long> lastByteRenderNanos = new ConcurrentHashMap<>();

        private LiveArtifactProgressListener(LiveRegion region, ConsoleStyle style) {
            this.region = region;
            this.style = style;
        }

        @Override
        public void onStart(ArtifactDescriptor descriptor) {
            region.render(style.work(SPINNER_GLYPH) + " " + label(descriptor));
        }

        @Override
        public void onComplete(ArtifactDescriptor descriptor, long bytes) {
            lastByteRenderNanos.remove(descriptor);
            region.commit(style.success(SUCCESS_GLYPH) + " " + label(descriptor) + " " + byteCount(bytes));
        }

        @Override
        public void onBytes(ArtifactDescriptor descriptor, long received, long total) {
            if (total < LARGE_ARTIFACT_BYTES || received <= 0L) {
                return;
            }
            long displayReceived = Math.min(received, total);
            if (displayReceived < total && !shouldRenderByteProgress(descriptor)) {
                return;
            }
            region.render(
                    style.work(bar(displayReceived, total))
                            + " "
                            + label(descriptor)
                            + " "
                            + byteCount(displayReceived)
                            + " / "
                            + byteCount(total));
        }

        @Override
        public void onFailure(ArtifactDescriptor descriptor, Throwable failure) {
            lastByteRenderNanos.remove(descriptor);
            region.commit(style.error(FAILURE_GLYPH) + " " + label(descriptor));
        }

        private boolean shouldRenderByteProgress(ArtifactDescriptor descriptor) {
            long now = System.nanoTime();
            Long previous = lastByteRenderNanos.get(descriptor);
            if (previous != null && now - previous < BYTE_RENDER_MIN_INTERVAL_NANOS) {
                return false;
            }
            lastByteRenderNanos.put(descriptor, now);
            return true;
        }

        private static String bar(long received, long total) {
            int filled = (int) Math.floor((double) received / (double) total * BAR_WIDTH);
            filled = Math.max(0, Math.min(BAR_WIDTH, filled));
            return "▓".repeat(filled) + "▒".repeat(BAR_WIDTH - filled);
        }

        private static String label(ArtifactDescriptor descriptor) {
            StringBuilder label = new StringBuilder(descriptor.coordinate().toString());
            descriptor.classifier().ifPresent(classifier -> label.append(':').append(classifier));
            if (!"jar".equals(descriptor.extension())) {
                label.append(':').append(descriptor.extension());
            }
            return label.toString();
        }

        private static String byteCount(long bytes) {
            if (bytes < 1024L) {
                return bytes + " B";
            }
            double kib = bytes / 1024.0;
            if (kib < 1024.0) {
                return String.format(Locale.ROOT, "%.1f KiB", kib);
            }
            return String.format(Locale.ROOT, "%.1f MiB", kib / 1024.0);
        }
    }
}
