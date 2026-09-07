package com.hudsonxm.bursttyping.engine;

import java.util.List;

public record TestRun(
    String target,
    List<Keystroke> keystrokes,
    long elapsedNanos,
    long completedAtEpochMillis
) {

    // Millis rather than Instant because serialization is easier and it will
    // only ever be used for ordering.
    public static TestRun from(TypingSession session) {
        return new TestRun(
            session.target(),
            session.keystrokes(),
            session.elapsedNanos(),
            System.currentTimeMillis());
    }

    // Stats are derived, not stored
    public double rawWpm() { return WpmCalculator.rawWpm(keystrokes, elapsedNanos); }
    public double netWpm() { return WpmCalculator.netWpm(keystrokes, elapsedNanos); }
    public double accuracy() { return WpmCalculator.accuracy(keystrokes); }

    public double elapsedSeconds() { return elapsedNanos / 1_000_000_000.0; }
}
