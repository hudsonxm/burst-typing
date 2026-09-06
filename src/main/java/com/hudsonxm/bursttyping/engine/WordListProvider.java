package com.hudsonxm.bursttyping.engine;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WordListProvider {
    
    private static final List<String> WORDS = List.of(
        "the","be","to","of","and","a","in","that","have","it",
        "for","not","on","with","he","as","you","do","at","this",
        "but","his","by","from","they","we","say","her","she","or",
        "will","my","one","all","would","there","their","what","so","up"
    );

    private final Random random = new Random();

    public String nextTest(int wordCount) {
        return IntStream.range(0, wordCount)
            .mapToObj(i -> WORDS.get(random.nextInt(WORDS.size())))
            .collect(Collectors.joining(" "));
    }
}
