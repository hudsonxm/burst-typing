package com.hudsonxm.bursttyping.engine;

public class TypingSession {

    public enum CharStatus { PENDING, CORRECT, INCORRECT}

    private final String target;
    private final char[] typed;
    private int cursor = 0;

    public TypingSession(String target) {
        this.target = target;
        this.typed = new char[target.length()];
    }

    public void accept(char c) {
        if (isComplete()) return;
        typed[cursor] = c;
        cursor++;
    }

    public void backspace() {
        if (cursor == 0) return;
        cursor--;
        typed[cursor] = 0;
    }

    public CharStatus statusAt(int index) {
        if (index >= cursor) return CharStatus.PENDING;
        return typed[index] == target.charAt(index) ? CharStatus.CORRECT : CharStatus.INCORRECT;
    }

    public boolean isComplete() { return cursor >= target.length(); }
    public int cursor() { return cursor; }
    public String target() { return target; }
    public int length() { return target.length(); }
}
