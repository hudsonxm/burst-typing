package com.hudsonxm.bursttyping.analytics;

import java.util.ArrayList;
import java.util.List;

public class LatencySamples {

    private final List<Long> samples = new ArrayList<>(4096);

    public void record(long nanos) {
        samples.add(nanos);
    }

    public int count() {
        return samples.size();
    }

    // Nearest-rank percentile. Empty set returns 0 rather than throwing.
    public long percentileNanos(double percentile) {
        if (samples.isEmpty()) return 0;
        List<Long> sorted = samples.stream().sorted().toList();
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    public double percentileMillis(double percentile) {
        return percentileNanos(percentile) / 1_000_000.0;
    }

    public List<Long> raw() {
        return List.copyOf(samples);
    }
}
