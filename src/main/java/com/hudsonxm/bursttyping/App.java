package com.hudsonxm.bursttyping;

import com.hudsonxm.bursttyping.engine.TypingSession;
import com.hudsonxm.bursttyping.engine.WordListProvider;
import com.hudsonxm.bursttyping.ui.TypingView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class App extends Application {

    private final WordListProvider words = new WordListProvider();

    @Override
    public void start(Stage stage) {
        Font.loadFont(
            getClass().getResourceAsStream("/fonts/JetBrainsMono-Regular.ttf"), 30);

        TypingView view = new TypingView();
        view.load(new TypingSession(words.nextTest(10)));

        Scene scene = new Scene(view, 1400, 700);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        
        scene.setOnKeyTyped(e -> view.handleTyped(e.getCharacter()));
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.BACK_SPACE) view.handleBackspace();
            if (e.getCode() == KeyCode.TAB) {
                view.load(new TypingSession(words.nextTest(10)));
                e.consume();
            }
        });

        stage.setScene(scene);
        stage.setTitle("burst-typing");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}