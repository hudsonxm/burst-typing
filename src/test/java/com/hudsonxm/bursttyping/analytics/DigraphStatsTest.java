package com.hudsonxm.bursttyping.analytics;

import com.hudsonxm.bursttyping.analytics.DigraphStats.DigraphStat;
import com.hudsonxm.bursttyping.engine.Keystroke;
import com.hudsonxm.bursttyping.engine.TestRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DigraphStatsTest {

    private static final long MS = 1_000_000L;

    // Target and timing fields don't participate in digraph analysis — only the
    // keystroke log does — so they're left at placeholder values throughout.
    private static TestRun run(Keystroke... ks) {
        return new TestRun("irrelevant", List.of(ks), 0L, 0L);
    }

    // Index is explicit rather than inferred from argument order: a pair is only
    // counted when the indices are consecutive, so it's the point of most tests here.
    private static Keystroke hit(char c, int index, long nanos) {
        return new Keystroke(c, c, index, nanos);
    }

    private static Keystroke miss(char typed, char expected, int index, long nanos) {
        return new Keystroke(typed, expected, index, nanos);
    }

    // One run containing exactly one t→h transition of the given gap.
    private static TestRun thRun(long gapNanos) {
        return run(hit('t', 0, 0), hit('h', 1, gapNanos));
    }

    private static DigraphStat find(List<DigraphStat> stats, String pair) {
        return stats.stream().filter(s -> s.pair().equals(pair)).findFirst().orElse(null);
    }

    @Test
    void singleTransitionIsItsOwnMedian() {
        List<DigraphStat> stats = DigraphStats.from(List.of(thRun(120 * MS)), 1);

        assertEquals(1, stats.size());
        assertEquals("th", stats.get(0).pair());
        assertEquals(120 * MS, stats.get(0).medianNanos());
        assertEquals(1, stats.get(0).samples());
    }

    @Test
    void oddSampleCountTakesTheMiddleValue() {
        List<DigraphStat> stats = DigraphStats.from(
            List.of(thRun(100 * MS), thRun(300 * MS), thRun(200 * MS)), 1);

        assertEquals(200 * MS, find(stats, "th").medianNanos());
        assertEquals(3, find(stats, "th").samples());
    }

    @Test
    void evenSampleCountAveragesTheTwoMiddleValues() {
        List<DigraphStat> stats = DigraphStats.from(
            List.of(thRun(400 * MS), thRun(100 * MS), thRun(300 * MS), thRun(200 * MS)), 1);

        // Middle two of {100, 200, 300, 400} are 200 and 300.
        assertEquals(250 * MS, find(stats, "th").medianNanos());
    }

    @Test
    void pairsBelowMinSamplesAreDropped() {
        List<DigraphStat> stats = DigraphStats.from(
            List.of(thRun(100 * MS), thRun(200 * MS),
                    run(hit('a', 0, 0), hit('b', 1, 50 * MS))), 2);

        assertNotNull(find(stats, "th"));
        assertNull(find(stats, "ab"), "ab has one sample, below the threshold of 2");
    }

    @Test
    void minSamplesIsInclusive() {
        List<TestRun> runs = List.of(thRun(100 * MS), thRun(200 * MS));

        assertNotNull(find(DigraphStats.from(runs, 2), "th"), "exactly minSamples should qualify");
        assertNull(find(DigraphStats.from(runs, 3), "th"));
    }

    @Test
    void nonConsecutiveIndicesAreNotAPair() {
        // "the" typed, backspaced away entirely, then retyped from the start. The
        // 'e' at index 2 and the retyped 't' at index 0 sit next to each other in
        // the log but were never typed as a pair — the 500ms between them is the
        // backspacing, not a keystroke transition.
        List<DigraphStat> stats = DigraphStats.from(
            List.of(run(
                hit('t', 0, 0),
                hit('h', 1, 100 * MS),
                hit('e', 2, 200 * MS),
                hit('t', 0, 700 * MS),      // retyped after three backspaces
                hit('h', 1, 800 * MS),
                hit('e', 2, 900 * MS))), 1);

        assertNull(find(stats, "et"), "e→t straddles the correction and was never typed");
        assertEquals(100 * MS, find(stats, "th").medianNanos());
        assertEquals(2, find(stats, "th").samples());
    }

    @Test
    void retypingTheSameIndexDoesNotPairWithItself() {
        // Backspacing one character and retyping it leaves two entries at the same
        // index. Equal indices aren't consecutive, so no self-pair is recorded.
        List<DigraphStat> stats = DigraphStats.from(
            List.of(run(
                hit('a', 0, 0),
                hit('b', 1, 100 * MS),
                hit('b', 1, 600 * MS))), 1);   // backspaced and retyped

        assertNull(find(stats, "bb"));
        assertEquals(100 * MS, find(stats, "ab").medianNanos());
    }

    @Test
    void incorrectKeystrokeBreaksBothAdjacentPairs() {
        // a → x(wrong) → c yields nothing: the mistyped key invalidates the
        // transition into it as well as the one out of it.
        List<DigraphStat> stats = DigraphStats.from(
            List.of(run(hit('a', 0, 0), miss('x', 'b', 1, 100 * MS), hit('c', 2, 200 * MS))), 1);

        assertTrue(stats.isEmpty(), "expected no pairs, got " + stats);
    }

    @Test
    void typingResumesProducingPairsAfterACorrection() {
        // Keystrokes are append-only, so a backspace-and-retype leaves the wrong
        // key in the log. The chain breaks around it but recovers straight after.
        List<DigraphStat> stats = DigraphStats.from(
            List.of(run(
                hit('a', 0, 0),
                miss('x', 'b', 1, 100 * MS),   // typo, backspaced
                hit('b', 1, 200 * MS),         // retyped correctly at the same index
                hit('c', 2, 300 * MS))), 1);

        assertEquals(1, stats.size());
        assertEquals("bc", stats.get(0).pair());
        assertEquals(100 * MS, stats.get(0).medianNanos());
    }

    @Test
    void spacesAreExcludedOnEitherSide() {
        List<DigraphStat> stats = DigraphStats.from(
            List.of(run(hit('a', 0, 0), hit(' ', 1, 100 * MS), hit('b', 2, 200 * MS))), 1);

        assertTrue(stats.isEmpty(), "expected no pairs, got " + stats);
    }

    @Test
    void slowestPairSortsFirst() {
        List<DigraphStat> stats = DigraphStats.from(
            List.of(run(hit('a', 0, 0), hit('b', 1, 50 * MS)),
                    run(hit('c', 0, 0), hit('d', 1, 300 * MS)),
                    run(hit('e', 0, 0), hit('f', 1, 150 * MS))), 1);

        assertEquals(List.of("cd", "ef", "ab"), stats.stream().map(DigraphStat::pair).toList());
    }

    @Test
    void transitionsDoNotSpanRunBoundaries() {
        // The last key of one run and the first of the next are not a digraph —
        // an arbitrary amount of idle time sits between them.
        List<DigraphStat> stats = DigraphStats.from(
            List.of(run(hit('x', 0, 0), hit('a', 1, 100 * MS)),
                    run(hit('b', 0, 200 * MS), hit('y', 1, 300 * MS))), 1);

        assertNotNull(find(stats, "xa"));
        assertNotNull(find(stats, "by"));
        assertNull(find(stats, "ab"), "a and b are in different runs");
    }

    @Test
    void emptyInputProducesNoStats() {
        assertTrue(DigraphStats.from(List.of(), 1).isEmpty());
        assertTrue(DigraphStats.from(List.of(run()), 1).isEmpty());
        assertTrue(DigraphStats.from(List.of(run(hit('a', 0, 0))), 1).isEmpty(),
            "a lone keystroke has no transition");
    }

    @Test
    void medianMillisConvertsFromNanos() {
        assertEquals(2.5, new DigraphStat("th", 2_500_000L, 1).medianMillis(), 0.0001);
    }
}
