package com.hudsonxm.bursttyping.engine;

import org.junit.jupiter.api.Test;
import static com.hudsonxm.bursttyping.engine.TypingSession.CharStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class TypingSessionTest {

    @Test
    void correctCharactersAreMarkedCorrect() {
        TypingSession s = new TypingSession("the");
        s.accept('t', System.nanoTime());
        s.accept('h', System.nanoTime());
        assertEquals(CORRECT, s.statusAt(0));
        assertEquals(CORRECT, s.statusAt(1));
        assertEquals(PENDING, s.statusAt(2));
    }

    @Test
    void wrongCharacterIsMarkedIncorrect() {
        TypingSession s = new TypingSession("the");
        s.accept('x', System.nanoTime());
        assertEquals(INCORRECT, s.statusAt(0));
    }

    @Test
    void backspaceRevertsToPending() {
        TypingSession s = new TypingSession("the");
        s.accept('t', System.nanoTime());
        s.backspace();
        assertEquals(PENDING, s.statusAt(0));
        assertEquals(0, s.cursor());
    }
}