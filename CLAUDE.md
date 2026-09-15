# burst-typing

A low-latency desktop typing test for short bursts, written in Java 21 + JavaFX.

## Why

Browser-based typing tests accumulate noticeable input lag at high speeds. At ~200 wpm a
10-word test finishes in under three seconds, so keystroke-to-pulse latency and timing
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
- Input-latency overlay on **F1**: p50/p95/p99/max of keystroke-to-pulse time for the current
  app session.

Planned but not built: error clustering, and a real distribution over recent runs (the
post-run history line is currently just best/avg/count).

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
│   ├── DigraphStats      from(runs, minSamples) → median latency per pair, slowest first
│   ├── LatencySamples    nearest-rank percentiles over a growing set of nanosecond samples
│   └── PulseLatencyTracker  buffers keystrokes awaiting a pulse, then samples each one
├── persistence/
│   ├── RunStore          interface: save(run), loadAll() oldest-first
│   └── JsonRunStore      Jackson + atomic temp-file-then-move write
└── ui/
    └── TypingView        one Text node per character, the caret, and five stat labels
src/main/resources/
├── css/app.css
├── fonts/JetBrainsMono-Regular.ttf  (+ OFL.txt)
└── words/burst-common.txt
src/test/java/com/hudsonxm/bursttyping/
├── analytics/DigraphStatsTest        14 tests
├── analytics/LatencySamplesTest       7 tests
├── analytics/PulseLatencyTrackerTest  9 tests
├── engine/TypingSessionTest           3 tests
├── engine/WpmCalculatorTest           6 tests
└── persistence/JsonRunStoreTest      10 tests, @TempDir — never touches ~/.burst-typing
```

Test packages mirror main packages. The three analytics tests live in `analytics/`, not
`engine/`. There is currently no test for `WordListProvider`.

`TypingView`'s post-run labels, top to bottom: `stats` (this run), `history` (best/avg/count),
`digraphs` (three slowest pairs), `latencies` (the F1 overlay, blank until pressed), `restart`
(the TAB hint).

Keys are bound at the scene level in `App`: printable characters via `setOnKeyTyped`, and
BACKSPACE / TAB (new test) / F1 (latency overlay) via `setOnKeyPressed`.

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

#### Timing and session state

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
- **Spaces are ordinary characters.** The ten words arrive as one space-joined string, which
  avoids a whole layer of word-boundary logic.

#### Rendering

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
- **A mistyped space gets its own style class**, `char-incorrect-space`. The glyph on screen
  is always the *target* character, so on a mistyped space there is nothing to color — the
  underline is the only thing that shows. It is currently identical to `char-incorrect` in
  `app.css`; it exists as a separate hook so the two can diverge.
- **The app does not pin the animation framerate.** JavaFX pulses at the display's refresh
  rate, and measured keystroke-to-pulse latency implies a ~5 ms interval on the development
  machine. An earlier `-Djavafx.animation.framerate=120` in `applicationDefaultJvmArgs` was
  removed: it only applies to `./gradlew run`, so launching from an IDE never picked it up.
  Re-add it there if you need a fixed rate, and set the same flag in the IDE's launch config.

#### Digraph analysis

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
- **Pairs touching a space are skipped entirely**, so only within-word transitions are
  measured. Space is struck with a thumb rather than a finger, so its timing reflects word
  rhythm rather than finger travel — and it would otherwise dominate, being half of every
  word-boundary pair.
- **Digraphs don't span run boundaries.** The keystroke loop is scoped per run and starts at
  `i = 1`, so the last key of one run is never paired with the first of the next. The index
  check backstops this — indices restart at 0 each run — but the scoping is the real guard.
- **`minSamples` is a per-pair threshold, not a per-run one.** Each letter pair has its own
  counter and they cross the threshold at different times, so the displayed list grows one
  entry at a time over the first several runs rather than appearing all at once. A 10-word
  test yields only ~39 within-word transitions spread across 100+ distinct pairs, so this is
  slower than it sounds.

#### Latency measurement

- **`LatencySamples` uses nearest-rank percentiles**, and returns 0 on an empty set rather
  than throwing, so the overlay has nothing to special-case beyond "no samples yet".
- **Keystroke-to-pulse is measured with a post-layout pulse listener.** `handleTyped` hands
  the keystroke's `nanoTime()` to `PulseLatencyTracker`, and a
  `Scene.addPostLayoutPulseListener` callback resolves everything pending on the next pulse.
  The listener is installed lazily on the first keystroke, because the view has no `Scene`
  at construction time.
- **More than one keystroke can be waiting on the same pulse**, so the tracker buffers up to
  16 and measures each from its own arrival — the pulse is a shared resolution point, not a
  shared start time. A single pending slot silently kept only the last keystroke, dropping
  the fast ones and biasing every percentile low during exactly the bursts this app exists
  to measure. Filling 16 slots needs ~960 keys/sec even at a slow 60 Hz pulse, against
  ~25/sec for a 300 wpm burst, so the
  buffer should never fill; if it ever does, the overflow is **counted and shown** in the
  overlay as `dropped=n` rather than quietly narrowing the sample set.
- **The tracker is JavaFX-free and unit-tested**, which is the whole reason it isn't a
  couple of fields on `TypingView`. `onKeystroke(nanos)`/`onPulse(nanos)` both take
  caller-supplied timestamps, following `TypingSession.accept`.
- **This is a lower bound on true keystroke-to-photon latency**, not the whole figure. It
  starts when JavaFX hands over the event — excluding the keyboard, USB polling, and the OS
  input stack — and ends after layout, excluding GPU compositing and display scanout.

### Build and run

```bash
./gradlew run     # launch
./gradlew test    # 49 tests across engine, analytics, persistence; no window opens
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
  Nothing enforces a maximum — the curated list's longest word is 8 characters, and that is
  the only thing preventing it. Adding a long word to `burst-common.txt` is enough to break
  the layout, with no error to say so.
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
- Word list is `words/burst-common.txt`: 303 hand-picked words, 2–8 characters, average 4.92,
  so a 10-word test is ~58 keystrokes. Curated for burst typing — common enough to be muscle
  memory, American spellings, no proper nouns, no awkward same-hand pile-ups.
  `WordListProvider` applies **no length filter**: every non-blank line is drawn, so an edit
  takes effect rather than being silently discarded. That also means the file is the only
  thing keeping words short enough for the single-line layout. Words are drawn with
  replacement, so one can repeat within a test — deliberate, Monkeytype does the same.
- The three digraph constants in `TypingView` interact and aren't independently tunable:
  narrowing `DIGRAPH_WINDOW` shrinks the sample pool, so pairs stop clearing
  `DIGRAPH_MIN_SAMPLES` and the line empties. Past 20 runs the window reaches a steady state
  and pairs sitting near the threshold flicker in and out between runs.
- Latency samples live in memory for the app session only — never persisted, never trimmed,
  and reset on restart. `percentileNanos` re-sorts the whole list on every call, which is
  acceptable only because it runs on an F1 press rather than per keystroke.
- Pulse latency is quantized by the pulse rate itself: a keystroke landing just after a pulse
  waits nearly a full interval, one landing just before it barely waits at all. The spread
  between p50 and p99 is therefore partly the pulse period (~5 ms as measured) rather than variance in
  the app, and no amount of sampling removes it.

## License

MIT. JetBrains Mono is bundled under OFL 1.1 with its license text alongside the font.
