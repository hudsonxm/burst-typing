package com.hudsonxm.bursttyping.ui;

import com.hudsonxm.bursttyping.engine.Keystroke;
import com.hudsonxm.bursttyping.engine.TestRun;
import com.hudsonxm.bursttyping.engine.TypingSession;
import com.hudsonxm.bursttyping.engine.WpmCalculator;
import com.hudsonxm.bursttyping.persistence.RunStore;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

import java.util.List;

public class TypingView extends StackPane {

    private final TextFlow flow = new TextFlow();
    private final Label stats = new Label();
    private final Label history = new Label();
    private final VBox column = new VBox(28);

    private final RunStore store;

    private TypingSession session;
    private Text[] charNodes;

    // Store is injected rather than constructed here so the view doesn't
    // depend on a concrete persistence choice.
    public TypingView(RunStore store) {
        this.store = store;

        flow.setTextAlignment(TextAlignment.CENTER);
        stats.getStyleClass().add("stats");
        history.getStyleClass().add("history");

        column.setAlignment(Pos.CENTER);
        column.getChildren().addAll(new Group(flow), stats, history);

        getChildren().add(column);
        setAlignment(Pos.CENTER);
    }

    public void load(TypingSession newSession) {
        this.session = newSession;
        this.charNodes = new Text[newSession.length()];
        flow.getChildren().clear();
        stats.setText("");
        history.setText("");

        for (int i = 0; i < newSession.length(); i++) {
            Text t = new Text(String.valueOf(newSession.target().charAt(i)));
            t.getStyleClass().add("char-pending");
            charNodes[i] = t;
            flow.getChildren().add(t);
        }
        moveCursor(-1, 0);
    }

    public void handleTyped(String character) {
        if (session == null || character.isEmpty()) return;
        char c = character.charAt(0);
        if (c < ' ') return;

        long nanos = System.nanoTime();

        int before = session.cursor();
        session.accept(c, nanos);
        if (session.cursor() == before) return;

        restyle(before);
        moveCursor(before, session.cursor());

        if (session.isComplete()) finishRun();
    }

    public void handleBackspace() {
        if (session == null) return;

        int before = session.cursor();
        session.backspace();
        if (session.cursor() == before) return;

        restyle(session.cursor());
        moveCursor(before, session.cursor());
    }

    private void finishRun() {
        TestRun run = TestRun.from(session);

        // Disk write on the FX thread. Acceptable because it only happens
        // after the test is over, never during typing.
        store.save(run);

        stats.setText(String.format(
            "%.0f wpm    %.0f raw    %.1f%% acc    %.2fs",
            run.netWpm(), run.rawWpm(), run.accuracy(), run.elapsedSeconds()));

        showHistory();
    }

    // Interim: real distribution work moves to analytics/ next.
    private void showHistory() {
        List<TestRun> all = store.loadAll();
        if (all.size() < 2) return;

        double best = all.stream().mapToDouble(TestRun::netWpm).max().orElse(0);
        double mean = all.stream().mapToDouble(TestRun::netWpm).average().orElse(0);

        history.setText(String.format(
            "best %.0f    avg %.0f    %d runs", best, mean, all.size()));
    }

    private void restyle(int index) {
        Text t = charNodes[index];
        t.getStyleClass().removeAll("char-pending", "char-correct", "char-incorrect");
        switch (session.statusAt(index)) {
            case PENDING   -> t.getStyleClass().add("char-pending");
            case CORRECT   -> t.getStyleClass().add("char-correct");
            case INCORRECT -> t.getStyleClass().add("char-incorrect");
        }
    }

    private void moveCursor(int from, int to) {
        if (from >= 0 && from < charNodes.length) charNodes[from].getStyleClass().remove("char-cursor");
        if (to   >= 0 && to   < charNodes.length) charNodes[to].getStyleClass().add("char-cursor");
    }
}