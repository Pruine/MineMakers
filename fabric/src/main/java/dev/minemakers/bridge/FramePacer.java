package dev.minemakers.bridge;

/** Keep frame deadlines stable instead of losing a frame to tiny timer variations. */
public final class FramePacer {
    private final long interval;
    private long next=Long.MIN_VALUE;
    public FramePacer(long interval) {
        if (interval<=0) throw new IllegalArgumentException("Frame interval must be positive");
        this.interval=interval;
    }
    public boolean due(long now) {
        if (next==Long.MIN_VALUE) { next=now+interval; return true; }
        if (now<next) return false;
        next+=interval;
        if (now-next>interval) next=now+interval; // skip a stall, never queue catch-up frames
        return true;
    }
}
