package com.hudsonxm.bursttyping.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hudsonxm.bursttyping.engine.TestRun;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class JsonRunStore implements RunStore {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;

    // Default location: ~/.burst-typing/runs.json — outside the repo, so run
    // history isn't tied to the working directory or accidentally committed.
    public JsonRunStore() {
        this(Path.of(System.getProperty("user.home"), ".burst-typing", "runs.json"));
    }

    // Path is injectable so tests can write to a temp directory.
    public JsonRunStore(Path file) {
        this.file = file;
    }

    @Override
    public List<TestRun> loadAll() {
        if (!Files.exists(file)) return List.of();
        try {
            return mapper.readValue(file.toFile(), new TypeReference<List<TestRun>>() {});
        } catch (IOException e) {
            // A corrupt or half-written file shouldn't crash the app on launch.
            // Losing history is bad; refusing to start is worse.
            System.err.println("Could not read run history, starting fresh: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public void save(TestRun run) {
        List<TestRun> all = new ArrayList<>(loadAll());
        all.add(run);
        writeAtomically(all);
    }

    // Write to a temp file then move it into place. A crash mid-write leaves
    // the old file intact rather than a truncated one.
    private void writeAtomically(List<TestRun> runs) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writeValue(tmp.toFile(), runs);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}