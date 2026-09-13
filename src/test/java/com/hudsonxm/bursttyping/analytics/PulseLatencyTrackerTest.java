package com.hudsonxm.bursttyping.analytics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PulseLatencyTrackerTest {

    private static final long MS = 1_000_000L;

    @Test
    void keystrokeResolvesOnTheNextPulse() {
        PulseLatencyTracker tracker = new PulseLatencyTracker();

        tracker.onKeystroke(0);
        tracker.onPulse(5 * MS);

        assertEquals(1, tracker.count());
        assertEquals(5 * MS, tracker.percentileNanos(50));
    }

    // The regression this class exists for. A single pending slot kept only the
    // last keystroke, so the 8ms sample vanished and the reported latency was
    // the 1ms one — fastest bursts dropped, every percentile biased low.
    @Test
    void everyKeystrokeInOnePulseGetsItsOwnSample() {
        PulseLatencyTracker tracker = new PulseLatencyTracker();

        tracker.onKeystroke(0);
        tracker.onKeystroke(3 * MS);
        tracker.onKeystroke(7 * MS);
        tracker.onPulse(8 * MS);

        assertEquals(3, tracker.count());
        assertEquals(1 * MS, tracker.percentileNanos(0));   // the last keystroke in
        assertEquals(8 * MS, tracker.percentileNanos(100)); // the first
        assertEquals(0, tracker.dropped());
    }

    @Test
    void samplesAreMeasuredFromEachKeystrokeNotTheFirst() {
        PulseLatencyTracker tracker = new PulseLatencyTracker();

        tracker.onKeystroke(2 * MS);
        tracker.onKeystroke(6 * MS);
        tracker.onPulse(10 * MS);

        // 8ms and 4ms, not 8ms twice.
        assertEquals(4 * MS, tracker.percentileNanos(0));
        assertEquals(8 * MS, tracker.percentileNanos(100));
    }

    @Test
    void aPulseWithNothingPendingRecordsNothing() {
        PulseLatencyTracker tracker = new PulseLatencyTracker();

        tracker.onPulse(5 * MS);
        tracker.onPulse(10 * MS);

        assertEquals(0, tracker.count());
    }

    @Test
    void pendingClearsSoAKeystrokeIsNeverCountedTwice() {
        PulseLatencyTracker tracker = new PulseLatencyTracker();

        tracker.onKeystroke(0);
        tracker.onPulse(5 * MS);
        tracker.onPulse(50 * MS); // nothing pending: must not re-measure the first keystroke

        assertEquals(1, tracker.count());
        assertEquals(5 * MS, tracker.percentileNanos(100));
        assertEquals(0, tracker.pending());
    }

    @Test
    void samplesAccumulateAcrossPulses() {
        PulseLatencyTracker tracker = new PulseLatencyTracker();

        tracker.onKeystroke(0);
        tracker.onPulse(4 * MS);
        tracker.onKeystroke(10 * MS);
        tracker.onPulse(12 * MS);

        assertEquals(2, tracker.count());
        assertEquals(2 * MS, tracker.percentileNanos(0));
        assertEquals(4 * MS, tracker.percentileNanos(100));
    }

    @Test
    void overflowIsCountedRatherThanDroppedSilently() {
        PulseLatencyTracker tracker = new PulseLatencyTracker();

        for (int i = 0; i < PulseLatencyTracker.MAX_PENDING + 3; i++) {
            tracker.onKeystroke(i * MS);
        }
        tracker.onPulse(100 * MS);

        assertEquals(PulseLatencyTracker.MAX_PENDING, tracker.count());
        assertEquals(3, tracker.dropped());
    }

    @Test
    void overflowDropsTheNewestAndKeepsWhatIsAlreadyPending() {
        PulseLatencyTracker tracker = new PulseLatencyTracker();

        for (int i = 0; i < PulseLatencyTracker.MAX_PENDING; i++) {
            tracker.onKeystroke(i * MS);
        }
        tracker.onKeystroke(999 * MS); // dropped: buffer full
        tracker.onPulse(1000 * MS);

        // The 1ms sample the dropped keystroke would have produced is absent.
        assertEquals(1000 * MS, tracker.percentileNanos(100));
        assertEquals(985 * MS, tracker.percentileNanos(0));
        assertEquals(1, tracker.dropped());
    }

    @Test
    void bufferSpaceIsReclaimedAfterEachPulse() {
        PulseLatencyTracker tracker = new PulseLatencyTracker();

        // Far more keystrokes than the buffer holds, but spread across pulses,
        // so the buffer drains between them and nothing is ever dropped.
        for (int run = 0; run < 5; run++) {
            for (int i = 0; i < PulseLatencyTracker.MAX_PENDING; i++) {
                tracker.onKeystroke(run * 100L * MS + i * MS);
            }
            tracker.onPulse(run * 100L * MS + 50 * MS);
        }

        assertEquals(5 * PulseLatencyTracker.MAX_PENDING, tracker.count());
        assertEquals(0, tracker.dropped());
    }
}
