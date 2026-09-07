package com.hudsonxm.bursttyping.engine;

import java.util.List;

public final class WpmCalculator {

    // Standard convention: a "word" is 5 characters, regardless
    // of actual word lengths. This is what monkeytype and every other test uses.
    private static final double CHARS_PER_WORD = 5.0;
    private static final double NANOS_PER_MINUTE = 60_000_000_000.0;

    private WpmCalculator() { } // static-only utility class

    // Raw: every keystroke counts, correct or not. Measures pure finger speed.
    public static double rawWpm(List<Keystroke> keystrokes, long elapsedNanos) {
        if (elapsedNanos <= 0) return 0;
        double minutes = elapsedNanos / NANOS_PER_MINUTE;
        return (keystrokes.size() / CHARS_PER_WORD) / minutes;
    }

    // Net: only correct keystrokes count. This is "real" WPM.
    public static double netWpm(List<Keystroke> keystrokes, long elapsedNanos) {
        if (elapsedNanos <= 0) return 0;
        long correct = keystrokes.stream().filter(Keystroke::correct).count();
        double minutes = elapsedNanos / NANOS_PER_MINUTE;
        return (correct / CHARS_PER_WORD) / minutes;
    }

    public static double accuracy(List<Keystroke> keystrokes) {
        if (keystrokes.isEmpty()) return 0;
        long correct = keystrokes.stream().filter(Keystroke::correct).count();
        return (double) correct / keystrokes.size() * 100.0;
    }
}
