package com.hudsonxm.bursttyping.engine;

public record Keystroke(char typed, char expected, int index, long nanos) {

    public boolean correct() {
        return typed == expected;
    }
}
