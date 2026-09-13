package com.hudsonxm.bursttyping.analytics;

// Pairs each keystroke with the render pulse that resolved it.
//
// More than one keystroke can be waiting on the same pulse, so a single slot
// loses the fast ones — and loses them precisely during the bursts this app
// exists to measure, biasing every percentile low. The buffer holds all of them.
public final class PulseLatencyTracker {

    // Sized well above anything physically reachable. The slowest plausible
    // pulse gives keystrokes the most time to pile up, so take that as the
    // bound: even at 60 Hz, filling 16 slots inside one pulse needs ~960
    // keys/sec. A 300 wpm burst is ~25 keys/sec.
    static final int MAX_PENDING = 16;

    private final LatencySamples samples = new LatencySamples();

    // Fixed array, never resized, so the input path stays allocation-free.
    private final long[] pending = new long[MAX_PENDING];
    private int pendingCount = 0;
    private int dropped = 0;

    // Both methods run on the FX thread: the keystroke handler and the
    // post-layout pulse callback. No synchronization needed, none implied.
    public void onKeystroke(long nanos) {
        if (pendingCount == pending.length) {
            // Counted rather than dropped quietly. Silently discarding a
            // keystroke is the exact bias this class removes, so if the buffer
            // ever does fill, the overlay says so instead of quietly lying.
            dropped++;
            return;
        }
        pending[pendingCount++] = nanos;
    }

    public void onPulse(long nanos) {
        // Every keystroke waiting on this pulse resolved at the same moment, so
        // each gets its own sample, measured from its own arrival.
        for (int i = 0; i < pendingCount; i++) {
            samples.record(nanos - pending[i]);
        }
        pendingCount = 0; // slots past the count are never read, so no clearing
    }

    public int count() { return samples.count(); }
    public int dropped() { return dropped; }
    public int pending() { return pendingCount; }

    public long percentileNanos(double percentile) { return samples.percentileNanos(percentile); }
    public double percentileMillis(double percentile) { return samples.percentileMillis(percentile); }
}
