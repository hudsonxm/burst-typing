package com.hudsonxm.bursttyping.persistence;

import com.hudsonxm.bursttyping.engine.Keystroke;
import com.hudsonxm.bursttyping.engine.TestRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JsonRunStoreTest {

    // The injectable Path constructor exists so these tests don't touch ~/.burst-typing.
    @TempDir
    Path dir;

    private static final long MS = 1_000_000L;

    private static Keystroke hit(char c, int index, long nanos) {
        return new Keystroke(c, c, index, nanos);
    }

    // A run of "ab" typed correctly, 100ms apart.
    private static TestRun sampleRun(long completedAt) {
        return new TestRun(
            "ab",
            List.of(hit('a', 0, 100 * MS), hit('b', 1, 200 * MS)),
            100 * MS,
            completedAt);
    }

    @Test
    void loadingAMissingFileGivesEmptyRatherThanThrowing() {
        JsonRunStore store = new JsonRunStore(dir.resolve("never-written.json"));

        assertEquals(List.of(), store.loadAll(), "a first launch has no history file yet");
    }

    @Test
    void aSavedRunComesBackIdentical() {
        JsonRunStore store = new JsonRunStore(dir.resolve("runs.json"));
        TestRun original = sampleRun(1_700_000_000_000L);

        store.save(original);

        assertEquals(List.of(original), store.loadAll());
    }

    @Test
    void keystrokeIndexSurvivesTheRoundTrip() {
        // Guards the silent failure mode described in CLAUDE.md: if a Keystroke field
        // stops being persisted, Jackson defaults it rather than erroring, the run still
        // loads, and only the digraph stats quietly go wrong. Assert the field directly
        // so a serialization regression fails here rather than in the analytics.
        JsonRunStore store = new JsonRunStore(dir.resolve("runs.json"));
        store.save(new TestRun(
            "xyz",
            List.of(hit('x', 0, 0), hit('y', 1, 100 * MS), hit('z', 2, 200 * MS)),
            200 * MS,
            0L));

        List<Keystroke> back = store.loadAll().get(0).keystrokes();

        assertEquals(List.of(0, 1, 2), back.stream().map(Keystroke::index).toList());
        assertEquals(List.of('x', 'y', 'z'), back.stream().map(Keystroke::typed).toList());
        assertEquals(List.of(0L, 100 * MS, 200 * MS), back.stream().map(Keystroke::nanos).toList());
    }

    @Test
    void derivedStatsStillComputeAfterReload() {
        // TestRun stores no stats — they're derived from keystrokes and elapsedNanos, so
        // this only holds if both survived intact.
        JsonRunStore store = new JsonRunStore(dir.resolve("runs.json"));
        TestRun original = sampleRun(0L);
        store.save(original);

        TestRun back = store.loadAll().get(0);

        assertEquals(original.netWpm(), back.netWpm(), 0.0001);
        assertEquals(original.rawWpm(), back.rawWpm(), 0.0001);
        assertEquals(original.accuracy(), back.accuracy(), 0.0001);
        assertEquals(original.elapsedSeconds(), back.elapsedSeconds(), 0.0001);
    }

    @Test
    void runsAccumulateInSaveOrderOldestFirst() {
        JsonRunStore store = new JsonRunStore(dir.resolve("runs.json"));
        store.save(sampleRun(1L));
        store.save(sampleRun(2L));
        store.save(sampleRun(3L));

        List<TestRun> all = store.loadAll();

        assertEquals(3, all.size());
        assertEquals(List.of(1L, 2L, 3L),
            all.stream().map(TestRun::completedAtEpochMillis).toList(),
            "RunStore documents loadAll() as oldest-first");
    }

    @Test
    void saveCreatesMissingParentDirectories() {
        // The default location is ~/.burst-typing/runs.json, which won't exist on first run.
        Path nested = dir.resolve("burst-typing").resolve("runs.json");
        JsonRunStore store = new JsonRunStore(nested);

        store.save(sampleRun(0L));

        assertTrue(Files.exists(nested));
        assertEquals(1, store.loadAll().size());
    }

    @Test
    void aCorruptFileLoadsAsEmptyInsteadOfCrashing() throws IOException {
        // Losing history is bad; refusing to start is worse. Prints to stderr and continues.
        Path file = dir.resolve("runs.json");
        Files.writeString(file, "{ this is not valid json");

        assertEquals(List.of(), new JsonRunStore(file).loadAll());
    }

    @Test
    void aCorruptFileIsOverwrittenByTheNextSave() throws IOException {
        Path file = dir.resolve("runs.json");
        Files.writeString(file, "garbage");
        JsonRunStore store = new JsonRunStore(file);

        store.save(sampleRun(7L));

        List<TestRun> all = store.loadAll();
        assertEquals(1, all.size(), "the unreadable history is discarded, not prepended to");
        assertEquals(7L, all.get(0).completedAtEpochMillis());
    }

    @Test
    void historyWrittenBeforeIndexExistedLoadsWithIndexSilentlyZeroed() throws IOException {
        // Characterises a known trap rather than endorsing it — see "runs.json is
        // unversioned" under Known limitations. A file written before Keystroke gained
        // `index` still deserializes: Jackson defaults the missing field, so the run loads,
        // wpm and accuracy stay correct, and only the digraph stats quietly go wrong
        // (every pair fails the index + 1 adjacency check). Nothing errors.
        //
        // If this test ever fails, someone has made deserialization strict or added a
        // schema version — both good. Update the limitation rather than this assertion.
        Path file = dir.resolve("runs.json");
        Files.writeString(file, """
            [{"target":"ab","elapsedNanos":100000000,"completedAtEpochMillis":1,
              "keystrokes":[{"typed":"a","expected":"a","nanos":100000000},
                            {"typed":"b","expected":"b","nanos":200000000}]}]""");

        List<TestRun> all = new JsonRunStore(file).loadAll();

        assertEquals(1, all.size(), "the legacy run still loads");
        assertEquals(List.of(0, 0), all.get(0).keystrokes().stream().map(Keystroke::index).toList(),
            "both keystrokes default to index 0, so they no longer look consecutive");
        assertEquals(240.0, all.get(0).netWpm(), 0.0001, "wpm is unaffected, which is why it's silent");
    }

    @Test
    void noTempFileIsLeftBehindAfterSave() throws IOException {
        Path file = dir.resolve("runs.json");
        JsonRunStore store = new JsonRunStore(file);

        store.save(sampleRun(0L));

        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = files.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(List.of("runs.json"), names,
                "writeAtomically() should move its temp file into place, not leave it");
        }
    }
}
