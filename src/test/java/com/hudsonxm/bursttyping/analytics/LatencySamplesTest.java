package com.hudsonxm.bursttyping.analytics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LatencySamplesTest {

    // 1..100 makes percentiles trivially checkable by hand: with 100 samples,
    // the Nth percentile is just the value N.
    private static LatencySamples oneToHundred() {
        LatencySamples s = new LatencySamples();
        for (int i = 1; i <= 100; i++) s.record(i);
        return s;
    }

    @Test
    void percentilesOnAKnownRange() {
        LatencySamples s = oneToHundred();
        assertEquals(50,  s.percentileNanos(50));
        assertEquals(99,  s.percentileNanos(99));
        assertEquals(100, s.percentileNanos(100));
        assertEquals(1,   s.percentileNanos(1));
    }

    @Test
    void insertionOrderDoesNotMatter() {
        LatencySamples s = new LatencySamples();
        for (int i = 100; i >= 1; i--) s.record(i);   // reversed
        assertEquals(50, s.percentileNanos(50));
    }

    @Test
    void medianIgnoresTheTailButP99DoesNot() {
        // The whole reason percentiles beat a mean: 10 slow samples out of
        // 1000 leave the median untouched and blow out p99.
        LatencySamples s = new LatencySamples();
        for (int i = 0; i < 980; i++) s.record(3_000_000L);    // 3ms
        for (int i = 0; i < 20;  i++) s.record(60_000_000L);   // 60ms - 2%, clear of the p99 boundary

        assertEquals(3.0,  s.percentileMillis(50), 0.001);
        assertEquals(60.0, s.percentileMillis(99), 0.001);
    }

    @Test
    void emptySamplesReturnZeroRatherThanThrowing() {
        LatencySamples s = new LatencySamples();
        assertEquals(0, s.percentileNanos(50));
        assertEquals(0, s.count());
    }

    @Test
    void singleSampleIsEveryPercentile() {
        LatencySamples s = new LatencySamples();
        s.record(42);
        assertEquals(42, s.percentileNanos(1));
        assertEquals(42, s.percentileNanos(50));
        assertEquals(42, s.percentileNanos(100));
    }

    @Test
    void sizeThatDoesNotDivideEvenly() {
        // 37 samples: p99 ceils to the last index rather than rounding down
        // and skipping the worst sample entirely.
        LatencySamples s = new LatencySamples();
        for (int i = 1; i <= 37; i++) s.record(i);
        assertEquals(37, s.percentileNanos(99));
    }

    @Test
    void rawIsDefensivelyCopied() {
        LatencySamples s = new LatencySamples();
        s.record(1);
        assertThrows(UnsupportedOperationException.class, () -> s.raw().add(2L));
    }
}