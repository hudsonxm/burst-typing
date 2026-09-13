package com.hudsonxm.bursttyping.engine;

import java.util.ArrayList;
import java.util.List;

public class TypingSession {

    public enum CharStatus { PENDING, CORRECT, INCORRECT}

    private final String target;
    private final char[] typed;
    private final List<Keystroke> keystrokes = new ArrayList<>();
    private int cursor = 0;
    
    // -1 means not started/completed, nanoTime() can be negative or zero so a sentinel is safer than checking for 0
    private long startNanos = -1;
    private long endNanos = -1;

    public TypingSession(String target) {
        this.target = target; // Every word in the typing test.
        this.typed = new char[target.length()];
    }

    // Caller supplies the timestamp rather than this method calling nanoTime()
    // to allow for more precise timing and testing.
    public void accept(char c, long nanos) {
        if (isComplete()) return;
        if (startNanos < 0) startNanos = nanos; // Clock starts on first keystroke, not when the session is created.

        keystrokes.add(new Keystroke(c, target.charAt(cursor), cursor, nanos));
        typed[cursor] = c;
        cursor++;

        if (isComplete()) endNanos = nanos;
    }

    public void backspace() {
        if (cursor == 0) return;
        if (isComplete()) return;
        cursor--;
        typed[cursor] = 0;
    }

    public CharStatus statusAt(int index) {
        if (index >= cursor) return CharStatus.PENDING; // Anything at or past the cursor is pending.
        return typed[index] == target.charAt(index) ? CharStatus.CORRECT : CharStatus.INCORRECT;
    }

    // Live-updating while running, frozen once endNanos is set.
    public long elapsedNanos() {
        if (startNanos < 0) return 0;
        return (endNanos < 0 ? System.nanoTime() : endNanos) - startNanos;
    }

    public boolean hasStarted() { return startNanos >= 0; }
    public List<Keystroke> keystrokes() { return keystrokes; }
    public boolean isComplete() { return cursor >= target.length(); }
    public int cursor() { return cursor; }
    public String target() { return target; }
    public int length() { return target.length(); }
}
