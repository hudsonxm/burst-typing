package com.hudsonxm.bursttyping.engine;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WpmCalculatorTest {

    private static final long ONE_MINUTE = 60_000_000_000L;

    // Helper: n keystrokes that all match, timestamps irrelevant here since
    // elapsed time is passed separately.
    private static List<Keystroke> correct(int n) {
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(i -> new Keystroke('a', 'a', i))
                .toList();
    }

    @Test
    void hundredCorrectCharsInOneMinuteIsTwentyWpm() {
        // 100 chars / 5 chars-per-word = 20 words, over 1 minute
        assertEquals(20.0, WpmCalculator.netWpm(correct(100), ONE_MINUTE), 0.001);
    }

    @Test
    void rawCountsIncorrectKeystrokesButNetDoesNot() {
        List<Keystroke> ks = List.of(
            new Keystroke('t', 't', 0),
            new Keystroke('x', 'h', 1),   // wrong
            new Keystroke('e', 'e', 2)
        );
        // raw: 3 chars, net: 2 chars — over 1 minute, so /5 gives 0.6 and 0.4
        assertEquals(0.6, WpmCalculator.rawWpm(ks, ONE_MINUTE), 0.001);
        assertEquals(0.4, WpmCalculator.netWpm(ks, ONE_MINUTE), 0.001);
    }

    @Test
    void accuracyIsPercentOfCorrectKeystrokes() {
        List<Keystroke> ks = List.of(
            new Keystroke('a', 'a', 0),
            new Keystroke('b', 'b', 1),
            new Keystroke('x', 'c', 2),
            new Keystroke('d', 'd', 3)
        );
        assertEquals(75.0, WpmCalculator.accuracy(ks), 0.001);
    }

    @Test
    void halvingTheTimeDoublesTheSpeed() {
        List<Keystroke> ks = correct(100);
        double full = WpmCalculator.netWpm(ks, ONE_MINUTE);
        double half = WpmCalculator.netWpm(ks, ONE_MINUTE / 2);
        assertEquals(full * 2, half, 0.001);
    }

    @Test
    void zeroElapsedDoesNotDivideByZero() {
        assertEquals(0.0, WpmCalculator.netWpm(correct(10), 0), 0.001);
        assertEquals(0.0, WpmCalculator.rawWpm(correct(10), 0), 0.001);
    }

    @Test
    void emptyKeystrokesGivesZeroAccuracy() {
        assertEquals(0.0, WpmCalculator.accuracy(List.of()), 0.001);
    }
}