package com.hudsonxm.bursttyping.engine;

public record Keystroke(char typed, char expected, long nanos) {

    public boolean isCorrect() {
        return typed == expected;
    }
}
