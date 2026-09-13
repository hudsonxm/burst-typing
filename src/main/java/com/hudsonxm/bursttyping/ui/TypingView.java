package com.hudsonxm.bursttyping.ui;

import com.hudsonxm.bursttyping.analytics.DigraphStats;
import com.hudsonxm.bursttyping.analytics.DigraphStats.DigraphStat;
import com.hudsonxm.bursttyping.analytics.PulseLatencyTracker;
import com.hudsonxm.bursttyping.engine.TestRun;
import com.hudsonxm.bursttyping.engine.TypingSession;
import com.hudsonxm.bursttyping.persistence.RunStore;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.List;
import java.util.stream.Collectors;

public class TypingView extends StackPane {

    // Half the blink cycle: fades out over this long, then back in over it again.
    private static final Duration BLINK = Duration.millis(530);

    private static final int DIGRAPH_MIN_SAMPLES = 5;
    private static final int DIGRAPHS_SHOWN = 3;
    private static final int DIGRAPH_WINDOW = 20;

    private final PulseLatencyTracker pulseLatency = new PulseLatencyTracker();
    private boolean pulseListenerInstalled;

    private final TextFlow flow = new TextFlow();
    private final Line caret = new Line();
    private final Group flowGroup = new Group(flow, caret);

    private final Label stats = new Label();
    private final Label history = new Label();
    private final Label digraphs = new Label();
    private final Label latencies = new Label();
    private final Label restart = new Label();
    private final VBox column = new VBox(28);

    // Fades out over the first half of the cycle and back in over the second.
    // A KeyValue's interpolator governs the interval *ending* at its frame, so
    // the easing belongs on the 530ms and 1060ms frames — one on Duration.ZERO
    // would have no interval to apply to. The closing frame must restate
    // opacity 1, or the caret sits dark and snaps on when the cycle repeats.
    private final Timeline blink = new Timeline(
        new KeyFrame(Duration.ZERO,     new KeyValue(caret.opacityProperty(), 1)),
        new KeyFrame(BLINK,             new KeyValue(caret.opacityProperty(), 0, Interpolator.EASE_BOTH)),
        new KeyFrame(BLINK.multiply(2), new KeyValue(caret.opacityProperty(), 1, Interpolator.EASE_BOTH)));

    private final RunStore store;

    private TypingSession session;
    private Text[] charNodes;
    private int caretIndex = -1;

    // Store is injected rather than constructed here so the view doesn't
    // depend on a concrete persistence choice.
    public TypingView(RunStore store) {
        this.store = store;

        flow.setTextAlignment(TextAlignment.CENTER);
        stats.getStyleClass().add("stats");
        history.getStyleClass().add("history");
        digraphs.getStyleClass().add("digraphs");
        restart.getStyleClass().add("restart");
        latencies.getStyleClass().add("latencies");

        caret.getStyleClass().add("caret");
        caret.setManaged(false);
        caret.setVisible(false);
        blink.setCycleCount(Animation.INDEFINITE);

        column.setAlignment(Pos.CENTER);
        column.getChildren().addAll(flowGroup, stats, history, digraphs, restart, latencies);

        getChildren().add(column);
        setAlignment(Pos.CENTER);
    }

    public void load(TypingSession newSession) {
        this.session = newSession;
        this.charNodes = new Text[newSession.length()];
        flow.getChildren().clear();
        stats.setText("");
        history.setText("");
        digraphs.setText("");
        restart.setText("");
        latencies.setText("");

        for (int i = 0; i < newSession.length(); i++) {
            Text t = new Text(String.valueOf(newSession.target().charAt(i)));
            t.getStyleClass().add("char-pending");
            charNodes[i] = t;
            flow.getChildren().add(t);
        }
        moveCursor(0);
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
        moveCursor(session.cursor());

        ensurePulseListener();
        pulseLatency.onKeystroke(nanos);

        if (session.isComplete()) finishRun();
    }

    public void handleBackspace() {
        if (session == null) return;

        int before = session.cursor();
        session.backspace();
        if (session.cursor() == before) return;

        restyle(session.cursor());
        moveCursor(session.cursor());
    }

    private void finishRun() {
        TestRun run = TestRun.from(session);

        // Disk write on the FX thread. Acceptable because it only happens
        // after the test is over, never during typing.
        store.save(run);

        // Read once after the save so the new run is included in the history and digraphs.
        List<TestRun> all = store.loadAll();

        stats.setText(String.format(
            "%.0f wpm    %.0f raw    %.1f%% acc    %.2fs",
            run.netWpm(), run.rawWpm(), run.accuracy(), run.elapsedSeconds()));

        restart.setText("Press TAB to restart");

        showHistory(all);
        showDigraphs(all);
    }

    // Interim: real distribution work moves to analytics/ next.
    private void showHistory(List<TestRun> all) {
        if (all.size() < 2) return;

        double best = all.stream().mapToDouble(TestRun::netWpm).max().orElse(0);
        double mean = all.stream().mapToDouble(TestRun::netWpm).average().orElse(0);

        history.setText(String.format(
            "best %.0f    avg %.0f    %d runs", best, mean, all.size()));
    }

    private void showDigraphs(List<TestRun> all) {
        // Limit the window to the most recent 20 runs so the digraphs reflect current
        // typing habits rather than converging to a fixed pattern.
        List<TestRun> recent = all.size() <= DIGRAPH_WINDOW
            ? all
            : all.subList(all.size() - DIGRAPH_WINDOW, all.size());

        List<DigraphStat> slowest = DigraphStats.from(recent, DIGRAPH_MIN_SAMPLES);
        if (slowest.isEmpty()) return; // nothing has hit the minimum sample threshold yet

        digraphs.setText("slowest:   " + slowest.stream()
            .limit(DIGRAPHS_SHOWN)
            .map(d -> String.format("%s %.0fms", d.pair(), d.medianMillis()))
            .collect(Collectors.joining("   ")));
    }

    public void showLatencies() {
        if (pulseLatency.count() == 0) {
            latencies.setText("no keystrokes measured yet");
            return;
        }

        // Shown only when non-zero, which should be never — but an overflow
        // means the numbers beside it are missing the fastest keystrokes.
        String dropped = pulseLatency.dropped() == 0
            ? ""
            : String.format("  dropped=%d", pulseLatency.dropped());

        latencies.setText(String.format(
            "keystroke->pulse   p50: %.1fms  p95: %.1fms  p99: %.1fms  max: %.1fms.  n=%d%s",
            pulseLatency.percentileMillis(50),
            pulseLatency.percentileMillis(95),
            pulseLatency.percentileMillis(99),
            pulseLatency.percentileMillis(100),
            pulseLatency.count(),
            dropped));
    }

    private void restyle(int index) {
        Text t = charNodes[index];
        t.getStyleClass().removeAll("char-pending", "char-correct", "char-incorrect", "char-incorrect-space");
        switch (session.statusAt(index)) {
            case PENDING   -> t.getStyleClass().add("char-pending");
            case CORRECT   -> t.getStyleClass().add("char-correct");
            case INCORRECT -> t.getStyleClass().add(
                session.target().charAt(index) == ' ' ? "char-incorrect-space" : "char-incorrect");
        }
    }

    private void moveCursor(int to) {
        caretIndex = to;
        boolean onChar = to >= 0 && to < charNodes.length;
        caret.setVisible(onChar);

        if (!onChar) { // past the last character: the run is over
            blink.stop();
            return;
        }
        placeCaret();

        // Restart the cycle on every move so the caret is solid the instant it
        // lands. Otherwise it can blink out exactly when you look for it.
        blink.playFromStart();
    }

    // Text nodes use LOGICAL bounds by default, so the height here is the font's
    // line height and is identical for every character — the caret doesn't grow
    // and shrink as it passes over ascenders and descenders.
    private void placeCaret() {
        if (charNodes == null || caretIndex < 0 || caretIndex >= charNodes.length) return;

        Bounds b = charNodes[caretIndex].getBoundsInParent();
        caret.setStartX(b.getMinX());
        caret.setEndX(b.getMinX());
        caret.setStartY(b.getMinY());
        caret.setEndY(b.getMaxY());
    }

    // Character bounds aren't known until the flow has been laid out, so the
    // placement done in moveCursor() is stale on first load and after a resize.
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        placeCaret();
    }

    private void ensurePulseListener() {
        if (pulseListenerInstalled) return;
        Scene scene = getScene();
        if (scene == null) return; // not yet attached to a scene

        scene.addPostLayoutPulseListener(this::recordPulseLatency);
        pulseListenerInstalled = true;
    }

    private void recordPulseLatency() {
        // No-ops when nothing is pending, which is most pulses.
        pulseLatency.onPulse(System.nanoTime());
    }
}