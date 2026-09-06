package com.hudsonxm.bursttyping.ui;

import com.hudsonxm.bursttyping.engine.TypingSession;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

public class TypingView extends StackPane{

    private final TextFlow flow = new TextFlow();
    private TypingSession session;
    private Text[] charNodes;

    public TypingView() {
        flow.setMaxWidth(Region.USE_PREF_SIZE);
        flow.setTextAlignment(TextAlignment.CENTER);
        getChildren().add(new Group(flow));
        setAlignment(Pos.CENTER);
    }

    public void load(TypingSession newSession) {
        this.session = newSession;
        this.charNodes = new Text[newSession.length()];
        flow.getChildren().clear();

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
        session.accept(c);
        if (session.cursor() == before) return;

        restyle(before);
        moveCursor(before, session.cursor());
    }

    public void handleBackspace() {
        if (session == null) return;

        int before = session.cursor();
        session.backspace();
        if (session.cursor() == before) return;

        restyle(session.cursor());
        moveCursor(before, session.cursor());
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
