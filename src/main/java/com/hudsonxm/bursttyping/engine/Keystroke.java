package com.hudsonxm.bursttyping.engine;

public record Keystroke(char typed, char expected, long nanos) {

    public boolean correct() {
        return typed == expected;
    }
}
