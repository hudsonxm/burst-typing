package com.hudsonxm.bursttyping.ui;

import java.util.List;

import com.hudsonxm.bursttyping.engine.Keystroke;
import com.hudsonxm.bursttyping.engine.TypingSession;
import com.hudsonxm.bursttyping.engine.WpmCalculator;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

public class TypingView extends StackPane{

    private final TextFlow flow = new TextFlow();
    private final Label stats = new Label();
    private final VBox column = new VBox(28); // gap between words and stats

    private TypingSession session;
    private Text[] charNodes;

    public TypingView() {
        flow.setTextAlignment(TextAlignment.CENTER);
        stats.getStyleClass().add("stats");

        column.setAlignment(Pos.CENTER);
        // Group wrapper is a hack to make the TextFlow center its content properly
        column.getChildren().addAll(new Group(flow), stats);

        getChildren().add(column);
        setAlignment(Pos.CENTER);
    }

    public void load(TypingSession newSession) {
        this.session = newSession;
        this.charNodes = new Text[newSession.length()];
        flow.getChildren().clear();
        stats.setText(""); // clear last run's numbers

        for (int i = 0; i < newSession.length(); i++) {
            Text charNode = new Text(String.valueOf(newSession.target().charAt(i)));
            charNode.getStyleClass().add("char-pending");
            charNodes[i] = charNode;
            flow.getChildren().add(charNode);
        }
        moveCursor(-1, 0);
    }

    public void handleTyped(String character) {
        if (session == null || character.isEmpty()) return;
        char c = character.charAt(0); // `character` is a String of length 1, convert to char here.
        if (c < ' ') return;

        int before = session.cursor();
        session.accept(c, System.nanoTime());
        if (session.cursor() == before) return;

        restyle(before);
        moveCursor(before, session.cursor());

        if (session.isComplete()) showResults();
    }

    public void handleBackspace() {
        if (session == null) return;

        int before = session.cursor();
        session.backspace();
        if (session.cursor() == before) return;

        restyle(session.cursor());
        moveCursor(before, session.cursor());
    }

    private void showResults() {
        List<Keystroke> ks = session.keystrokes();
        long elapsed = session.elapsedNanos();

        stats.setText(String.format(
            "%.0f wpm     %.0f raw     %.1f%% acc     %.2fs",
            WpmCalculator.netWpm(ks, elapsed),
            WpmCalculator.rawWpm(ks, elapsed),
            WpmCalculator.accuracy(ks),
            elapsed / 1_000_000_000.0));
    }

    private void restyle(int index) {
        Text t = charNodes[index];
        t.getStyleClass().removeAll("char-pending", "char-correct", "char-incorrect");
        switch (session.statusAt(index)) {
            case PENDING -> t.getStyleClass().add("char-pending");
            case CORRECT -> t.getStyleClass().add("char-correct");
            case INCORRECT -> t.getStyleClass().add("char-incorrect");
        }
    }

    private void moveCursor(int from, int to) {
        if (from >= 0 && from < charNodes.length) charNodes[from].getStyleClass().remove("char-cursor");
        if (to   >= 0 && to   < charNodes.length) charNodes[to].getStyleClass().add("char-cursor");
    }

}
