# burst-typing

A low-latency desktop typing test for short bursts, written in Java 21 + JavaFX.

## Why

Browser-based typing tests accumulate noticeable input lag at high speeds. At ~200 wpm a
10-word test finishes in under three seconds, so keystroke-to-paint latency and timing
precision dominate the result — a 50 ms error is ~2% of the entire run.

This project exists to (a) remove the browser from the input path, and (b) treat a single
run's wpm as the near-meaningless number it is, reporting run-to-run distribution instead.

## What

- **10-word tests only.** Not a configurable length. The short format is the whole point:
  it's what makes timing precision matter and single-run variance unavoidable.
- Per-keystroke capture (character, expected character, `System.nanoTime()` timestamp),
  retained append-only so corrections stay in the record.
- Raw wpm, net wpm, accuracy, elapsed time.

Planned but not built: run history persistence, digraph latency analysis, error clustering,
distribution over recent runs, keystroke-to-paint measurement.

## How

### Layout

```
src/main/java/com/hudsonxm/bursttyping/
├── App.java              JavaFX entry point, scene-level key handlers
├── engine/               pure logic — no JavaFX
│   ├── TypingSession     state machine: cursor, typed vs. target, keystroke log
│   ├── Keystroke         record(char typed, char expected, long nanos)
│   ├── WpmCalculator     static utility: rawWpm, netWpm, accuracy
│   └── WordListProvider  nextTest(n) → n random words, space-joined (App passes 10)
└── ui/
    └── TypingView        one Text node per character, plus the stats line
src/main/resources/
├── css/app.css
└── fonts/JetBrainsMono-Regular.ttf  (+ OFL.txt)
src/test/java/com/hudsonxm/bursttyping/engine/
├── TypingSessionTest
└── WpmCalculatorTest
```

### The one hard rule

**`engine/` and `analytics/` contain zero `javafx.*` imports.** Not "mostly" — zero. This is
what keeps the logic unit-testable without spinning up a UI toolkit, and it's verifiable:

```powershell
Select-String -Path src/main/java/com/hudsonxm/bursttyping/engine/*.java -Pattern javafx
```

That should return nothing. `analytics/` will be held to the same rule when it exists.

### Design decisions worth knowing

- **`accept(char, long)` takes a caller-supplied timestamp** rather than calling
  `nanoTime()` itself. Keeps the session deterministic under test.
- **The clock starts on the first keystroke**, not on construction. Idle time before you
  begin typing isn't part of the run.
- **`startNanos`/`endNanos` use `-1` as a sentinel**, not `0` — `nanoTime()` values can
  legitimately be zero or negative.
- **`statusAt()` computes, it doesn't cache.** Status is derived fresh from comparing
  `typed[i]` to `target.charAt(i)`, with an `index >= cursor` guard for untyped positions.
  No cache means no cache invalidation bug on backspace.
- **The keystroke list is append-only.** Backspace decrements the cursor and clears
  `typed[cursor]`, but logs nothing and removes nothing. This is what makes raw-vs-net wpm
  possible: type wrong, backspace, fix, and the log holds two entries of which one is
  correct — raw wpm counts both, net counts only the one.
- **One `Text` node per character**, held in an array indexed identically to the session,
  so a keystroke restyles a fixed handful of nodes — the character just typed, plus the
  `char-cursor` class moving off one node and onto the next — and nothing else redraws.
- **Spaces are ordinary characters.** The ten words arrive as one space-joined string, which
  avoids a whole layer of word-boundary logic.

### Build and run

```bash
./gradlew run     # launch
./gradlew test    # engine tests, no window opens
```

Java 21 toolchain, JavaFX via the `org.openjfx.javafxplugin` Gradle plugin. `build.gradle`
also declares `jackson-databind`, staged for run-history persistence and not yet referenced
by any code.

The wrapper is committed, so Gradle doesn't need to be installed. `.gitignore` ignores
`*.jar` broadly and then negates `gradle/wrapper/gradle-wrapper.jar`; keep that negation if
you ever rework the ignore rules, or a fresh clone loses `./gradlew`.

### Conventions

- Commit subjects in imperative mood, under ~50 chars; detail in the body.
- Brief inline comments on non-obvious decisions; skip the obvious ones.
- New logic goes in `engine/` or `analytics/` with a test before it gets a UI.

## Known limitations

- Typing past the end of the test drops the extra characters. Monkeytype shows them in red
  past the word; doing that would decouple display length from target length.
- `keystrokes.size()` can exceed `target.length()` after backspace-and-retype. Correct for
  accuracy, but the analytics layer needs to expect it.
- Single-line layout with no wrap. A draw of unusually long words can overflow the window.
  Fixes when it matters: cap word length, or scale font to fit.
- Word list is 40 words hardcoded in `WordListProvider`. Moving to a resource file is next.

## License

MIT. JetBrains Mono is bundled under OFL 1.1 with its license text alongside the font.