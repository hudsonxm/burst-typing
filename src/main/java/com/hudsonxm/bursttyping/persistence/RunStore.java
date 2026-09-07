package com.hudsonxm.bursttyping.persistence;

import java.util.List;

import com.hudsonxm.bursttyping.engine.TestRun;

public interface RunStore {

    void save(TestRun run);

    // Ordered oldest-first, callers that want "last N" should take from the end.
    List<TestRun> loadAll();
}
