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
- Per-keystroke capture (character, expected character, target index, `System.nanoTime()`
  timestamp), retained append-only so corrections stay in the record.
- Raw wpm, net wpm, accuracy, elapsed time.
- Run history persisted to `~/.burst-typing/runs.json`, with best/average shown after a run.
- Digraph latency: median time between consecutively-typed correct character pairs, over the
  last 20 runs, three slowest shown after each test.

Planned but not built: error clustering, distribution over recent runs (the post-run line is
currently just best/avg/count), keystroke-to-paint measurement.

## How

### Layout

```
src/main/java/com/hudsonxm/bursttyping/
├── App.java              JavaFX entry point, scene-level key handlers
├── engine/               pure logic — no JavaFX
│   ├── TypingSession     state machine: cursor, typed vs. target, keystroke log
│   ├── Keystroke         record(char typed, char expected, int index, long nanos)
│   ├── TestRun           record: target, keystrokes, elapsed, completedAt; stats derived
│   ├── WpmCalculator     static utility: rawWpm, netWpm, accuracy
│   └── WordListProvider  nextTest(n) → n random words, space-joined (App passes 10)
├── analytics/            pure logic — no JavaFX
│   └── DigraphStats      from(runs, minSamples) → median latency per pair, slowest first
├── persistence/
│   ├── RunStore          interface: save(run), loadAll() oldest-first
│   └── JsonRunStore      Jackson + atomic temp-file-then-move write
└── ui/
    └── TypingView        one Text node per character, the caret, and four stat labels
src/main/resources/
├── css/app.css
├── fonts/JetBrainsMono-Regular.ttf  (+ OFL.txt)
└── words/english-1k.txt
src/test/java/com/hudsonxm/bursttyping/
├── analytics/DigraphStatsTest        14 tests
└── engine/TypingSessionTest          3 tests
    engine/WpmCalculatorTest          6 tests
```

Test packages mirror main packages. `DigraphStatsTest` lives in `analytics/`, not `engine/`.

`TypingView`'s post-run labels, top to bottom: `stats` (this run), `history` (best/avg/count),
`digraphs` (three slowest pairs), `restart` (the TAB hint).

### The one hard rule

**`engine/` and `analytics/` contain zero `javafx.*` imports.** Not "mostly" — zero. This is
what keeps the logic unit-testable without spinning up a UI toolkit, and it's verifiable:

```bash
grep -rn javafx src/main/java/com/hudsonxm/bursttyping/engine/ \
                src/main/java/com/hudsonxm/bursttyping/analytics/
```

That should return nothing. `persistence/` happens to be JavaFX-free too, but that's not
load-bearing — the rule is about `engine/` and `analytics/`.

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
  so a keystroke restyles exactly one node — the character just typed — and moves the caret.
  Nothing else redraws.
- **The caret is its own `Line` node**, an unmanaged sibling of the `TextFlow` inside a
  `Group`, so it can be positioned absolutely without taking part in text layout and
  displacing characters. It reads `getBoundsInParent()` of the current character; because
  `Text` uses LOGICAL bounds, that height is the font line height and is identical for every
  character, so the caret doesn't grow and shrink over ascenders. `layoutChildren()` re-places
  it, since character bounds aren't known on first load or after a resize.
- **The blink is a `Timeline` on `opacity`, not CSS** — JavaFX CSS has no animation support,
  and `Text` has no border property, so an underline was the only pure-CSS option. Note that
  a `KeyValue`'s interpolator governs the interval *ending* at its frame, and the closing
  frame must restate opacity 1 or the caret snaps rather than fades on the loop.
- **Spaces are ordinary characters.** The ten words arrive as one space-joined string, which
  avoids a whole layer of word-boundary logic.
- **`Keystroke.index` records the target position the key was typed at**, and it exists purely
  so analytics can tell adjacency in the *log* from adjacency in the *text*. Because
  `backspace()` records nothing, those two are not the same thing: type `the`, backspace it
  away, retype it, and the log holds `…e, t…` with the correction time between them. Without
  the index that reads as a genuine `et` digraph, several hundred milliseconds slow, and it
  sorts straight to the top of the slowest list. `DigraphStats` therefore requires
  `curr.index() == prev.index() + 1`. The median does not save you here — a pair that only
  ever arises by straddling a correction has *every* sample inflated.
- **`DigraphStats` also requires both keys to be correct**, so a typo breaks the chain on both
  sides of itself: `a → x(typo) → b → c` yields only `bc`. A pair whose timing includes a
  correction isn't measuring that pair.
- **Digraphs don't span run boundaries.** The keystroke loop is scoped per run and starts at
  `i = 1`, so the last key of one run is never paired with the first of the next. The index
  check backstops this — indices restart at 0 each run — but the scoping is the real guard.
- **`minSamples` is a per-pair threshold, not a per-run one.** Each letter pair has its own
  counter and they cross the threshold at different times, so the displayed list grows one
  entry at a time over the first several runs rather than appearing all at once. A 10-word
  test is ~40 transitions spread across 100+ distinct pairs, so this is slower than it sounds.

### Build and run

```bash
./gradlew run     # launch
./gradlew test    # 23 engine + analytics tests, no window opens
```

Java 21 toolchain, JavaFX 21.0.2 (`javafx.controls` only) via the `org.openjfx.javafxplugin`
Gradle plugin. `jackson-databind` backs `JsonRunStore`.

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
  accuracy, and `DigraphStats` handles it, but anything new in `analytics/` must expect it.
- Single-line layout with no wrap. A draw of unusually long words can overflow the window.
  `WordListProvider` caps words at 8 characters, which makes this unlikely but not impossible.
- `JsonRunStore.save()` rewrites the entire history file on every run, and `TypingView` does
  it on the FX thread. Fine at current sizes — it only happens once a test is over, never
  during typing — but it's O(history) per run and will need revisiting.
- **`runs.json` is unversioned, and its schema has already changed once.** Adding
  `Keystroke.index` broke every run written before it, but quietly: Jackson defaults the
  missing field to 0, so the run still loads and still counts for wpm and accuracy, while
  contributing nothing to digraph stats because every pair fails the `index + 1` check. No
  error, no warning. That history was discarded rather than migrated, so nothing stale remains
  today — but the next field added to `Keystroke` or `TestRun` will fail the same silent way,
  and there's no version field to detect it against.
- Word list is `words/english-1k.txt`, roughly a thousand lines, of which ~890 survive the
  2–8 character filter in `WordListProvider` (`MIN_WORD_LENGTH`/`MAX_WORD_LENGTH`). Words are
  drawn with replacement, so a word can repeat within one test — deliberate, Monkeytype does
  the same.
- `persistence/` has no tests. `JsonRunStore` does file I/O, atomic replacement, and JSON
  round-tripping of the whole model, and none of it is covered — the constructor already
  takes an injectable `Path` for exactly this purpose.
- The three digraph constants in `TypingView` interact and aren't independently tunable:
  narrowing `DIGRAPH_WINDOW` shrinks the sample pool, so pairs stop clearing
  `DIGRAPH_MIN_SAMPLES` and the line empties. Past 20 runs the window reaches a steady state
  and pairs sitting near the threshold flicker in and out between runs.

## License

MIT. JetBrains Mono is bundled under OFL 1.1 with its license text alongside the font.