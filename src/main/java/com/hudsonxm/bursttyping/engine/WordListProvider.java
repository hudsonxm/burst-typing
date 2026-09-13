package com.hudsonxm.bursttyping.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WordListProvider {
    
    private static final String RESOURCE = "/words/burst-common.txt";

    private final List<String> words;
    private final Random random = new Random();

    public WordListProvider() {
        this(RESOURCE);
    }

    // Path is injectable so tests can load a small fixture instead of the full word list.
    public WordListProvider(String resourcePath) {
        this.words = load(resourcePath);
    }

    private static List<String> load(String resourcePath) {
        try (InputStream in = WordListProvider.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Word list not found: " + resourcePath);
            }
            // No length filtering: the list is curated, so every line is a word we
            // actually want drawn. A filter here would silently discard edits instead.
            List<String> loaded = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();

            if (loaded.isEmpty()) {
                throw new IllegalStateException("Word list is empty: " + resourcePath);
            }
            return loaded;
        } catch (IOException e) {
            // Reading a bundled resource shouldn't fail; if it does, the jar
            // is broken and there's no sensible recovery.
            throw new UncheckedIOException(e);
        }
    }

    // Words are drawn with replacement, so the same word can appear multiple times in a test.
    // This is intentional, monkeytype behaves the same way.
    public String nextTest(int wordCount) {
        return IntStream.range(0, wordCount)
            .mapToObj(i -> words.get(random.nextInt(words.size())))
            .collect(Collectors.joining(" "));
    }

    public int size() {
        return words.size();
    }
}
