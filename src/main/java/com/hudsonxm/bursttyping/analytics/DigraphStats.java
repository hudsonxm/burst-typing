package com.hudsonxm.bursttyping.analytics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hudsonxm.bursttyping.engine.Keystroke;
import com.hudsonxm.bursttyping.engine.TestRun;

public final class DigraphStats {

    // Nested since it's a simple data holder and doesn't need to be used outside this context.
    public record DigraphStat(String pair, long medianNanos, int samples) {
        public double medianMillis() { return medianNanos / 1_000_000.0; }
    }

    private DigraphStats() { } // static-only utility class

    // minSamples: ignore digraphs that don't have enough data to be meaningful.
    public static List<DigraphStat> from(List<TestRun> runs, int minSamples) {
        Map<String, List<Long>> latencies = new HashMap<>();

        for (TestRun run : runs) {
            List<Keystroke> ks = run.keystrokes();

            for (int i = 1; i < ks.size(); i++) {
                Keystroke prev = ks.get(i - 1);
                Keystroke curr = ks.get(i);

                if (curr.index() != prev.index() + 1) continue; // A backspace moved the cursor.

                if (!prev.correct() || !curr.correct()) continue; // Only consider correct transitions.

                if (prev.typed() == ' ' || curr.typed() == ' ') continue; // Ignore spaces.

                String pair = "" + prev.typed() + curr.typed();
                latencies.computeIfAbsent(pair, k -> new ArrayList<>()).add(curr.nanos() - prev.nanos());
            }
        }

        return latencies.entrySet().stream()
            .filter(e -> e.getValue().size() >= minSamples)
            .map(e -> new DigraphStat(e.getKey(), median(e.getValue()), e.getValue().size()))
            .sorted(Comparator.comparingLong(DigraphStat::medianNanos).reversed())
            .toList();
    }

    private static long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1
            ? sorted.get(n / 2)
            : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }
}
