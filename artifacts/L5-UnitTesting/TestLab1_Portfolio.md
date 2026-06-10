# TestLab1 — Unit Tests for the Undo/Redo Feature (Portfolio Artifact)

**Course:** Software Maintenance
**Student:** Ignas Beinoras (GitHub: IgnasBeinoras)
**Case study:** JHotDraw — feature: *Undo / Redo of editing operations*
**Date:** 2026-04-30
**Linked artifacts:** `ChangeReqLab1_UserStory.md`, `CLLab1_ConceptLocation.md`, `AnalysisLab1_ImpactSet.md`, `RefactoringLab1_Portfolio.md`, `ActualizationLab1_Portfolio.md`
**Local-only:** all paths refer to the local working copy at `Software maintinence labs/JHotDraw`. No commits, no push.

---

## 1. What I tested and why

The lab brief asks for class-level unit tests of *"important business
functionality of your selected feature"*. The selected feature is
**Undo / Redo of editing operations** (`ChangeReqLab1_UserStory.md`),
and the *important business functionality* of that feature is
concentrated in two CHANGED-ring classes from
`AnalysisLab1_ImpactSet.md`:

| Class | Why this is the core business logic |
|-------|-------------------------------------|
| `org.jhotdraw.undo.UndoRedoManager` (jhotdraw-utils) | Holds the linear undo/redo stack, owns the `undoOrRedoInProgress` re-entrancy invariant, tracks `hasSignificantEdits` (the dirty-state flag), and produces the menu actions whose label/enabled state the user actually sees. |
| `org.jhotdraw.undo.CompositeEdit` (jhotdraw-utils) | Aggregates several `UndoableEdit`s into one user-visible undo step (multi-figure move, find-and-replace, group). Carries the `addEdit(this) ⇒ end()` protocol that distinguishes it from JDK `CompoundEdit`. |

Both are framework-thin and carry feature invariants that are not
exercised by the existing `BezierPathNGTest` (the only pre-existing test
in the module). They are the right unit-test targets per the lab brief.

The application-level controllers `UndoAction`/`RedoAction` (jhotdraw-actions)
were intentionally not unit-tested in this lab — after the L3 refactoring
they are 41-line shims that would mostly test the JDK `AbstractAction`
plumbing. Their behaviour is exercised end-to-end by the manual
acceptance criteria in `ChangeReqLab1_UserStory.md`.

---

## 2. Tooling and test-framework choice

| Decision | Choice | Reason |
|----------|--------|--------|
| Test framework | **JUnit 4.13.2** | The lab brief specifies it ("Swing and JUnit extensions often work best with JUnit 4"). `jhotdraw-samples-misc` already declares the same version, so it's consistent across the reactor. |
| Mocking | **Mockito 4.11.0** | Mockito 5+ requires Java 11; JHotDraw's `pom.xml` pins `maven.compiler.source/target = 1.8`, so 4.x is the right line. |
| Surefire provider | TestNG (auto-selected) | The module already had `org.testng:testng:6.8.21` — Maven Surefire's TestNG provider transparently runs JUnit 4 tests on top, so I did not need to switch providers (28 JUnit-4 tests + 1 pre-existing TestNG test all run in the same `mvn test` execution). |
| Resource bundle | Test-only copy of `org.jhotdraw.undo.Labels.properties` | The production bundle physically lives in **jhotdraw-core** even though the consuming class lives in **jhotdraw-utils** (a structural smell flagged in §6 below). I copied it into `src/test/resources/` so the manager can be instantiated in tests without depending on jhotdraw-core. |

`jhotdraw-utils/pom.xml` was extended (one block, three dependencies)
to add JUnit 4 + Mockito alongside the existing TestNG entry. No other
build files were touched.

---

## 3. Test inventory (28 tests)

### `org.jhotdraw.undo.UndoRedoManagerTest` (16 tests)

**Best-case (8):**

1. `freshManager_hasNoEditsAndDisabledMenuActions` — invariants on construction.
2. `addEdit_significant_makesUndoableAndEnablesUndoAction` — happy path: add → can undo, undo action enabled, dirty flag set.
3. `undo_movesEditFromUndoStackToRedoStack` — `undo()` calls the edit's `undo()` once, redo stack becomes available.
4. `redo_replaysEdit` — `redo()` calls the edit's `redo()` once, redo stack empties.
5. `undoOrRedo_picksUndoFirst_thenRedo` — heuristic dispatch covered.
6. `discardAllEdits_clearsStateAndSignificance` — both stacks cleared, dirty flag reset, menu disabled.
7. `addEdit_insignificant_doesNotMarkSignificant` — significance is gated by `isSignificant()`.
8. `setHasSignificantEdits_firesPropertyChange` — observer wiring works for the dirty-state property.

**Boundary (5):**

9. `undo_onEmptyManager_throws` — `CannotUndoException`.
10. `redo_withoutUndo_throws` — `CannotRedoException`.
11. `addEditDuringUndo_isIgnoredAndEditIsDied` — the *headline invariant*: while a re-entrant `undo()` is in flight any incoming `addEdit` is dropped and `die()`'d (this is the whole reason `undoOrRedoInProgress` exists). Implemented by stubbing the primary edit's `undo()` with a `Mockito.doAnswer` that fires a re-entrant `manager.addEdit(secondary)`.
12. `undo_resetsGuardEvenWhenUndoableEditThrows` — `runGuarded` must restore the flag in `finally` even when the underlying edit throws `CannotUndoException`. Verified indirectly: a fresh edit added after the failure must not be died.
13. `undoMenuActionLabel_isPresentBothEmptyAndPopulated` — fallback resource label kicks in when stack empty; presentation name kicks in when populated.

**Listener wiring (3):** `removePropertyChangeListener_actuallyUnsubscribes`, plus the listener subjects above.

### `org.jhotdraw.undo.CompositeEditTest` (12 tests)

**Best-case (6):**

1. `defaultConstructor_isSignificantAndUsesSuperPresentationName`
2. `presentationNameConstructor_overridesSuperName`
3. `significanceConstructor_overridesSignificanceFlag` — both `true` and `false` cases.
4. `undoAndRedoPresentationNames_includeBundleLabel` — verifies the `"Undo Move Figures"` / `"Redo Move Figures"` localised assembly.
5. `undo_callsUndoOnEveryChildInReverseOrder` — composite as a one-step user-visible undo.
6. `redo_callsRedoOnEveryChildInOriginalOrder`.

**Boundary (6):**

7. `addEdit_self_endsTheCompositeAndReturnsTrue` — the `addEdit(this) ⇒ end()` protocol.
8. `addEdit_nestedCompositeWhileInProgress_isIgnored` — the `instanceof CompositeEdit && isInProgress()` short-circuit (silently accepted, *not* stored). Implemented with `Mockito.spy` so the production code's `instanceof` check still fires while the test can `verify(spy, never()).undo()`.
9. `setSignificant_canSilenceAComposite` — the override path on `isSignificant()`.
10. `emptyComposite_isInsignificantEvenWhenFlagIsTrue` — the AND-with-super.isSignificant() interaction.
11–12. Constructor coverage for the two-arg variants.

---

## 4. Single-method-single-path discipline + mocks

The lab brief's rule 4(a) is: *"A unit test should test a single
code-path through a single method. When the execution of a method
passes outside of that method, you have a dependency and should apply
mocks/stubs to avoid the dependency."*

The tests follow that rule:

- `UndoRedoManager.addEdit(UndoableEdit)` reaches into the edit's
  `canUndo`, `isSignificant`, `addEdit(other)`, `replaceEdit(other)`,
  and (during undo) `die`. Every one of those collaborator methods is
  on a Mockito mock — no `AbstractUndoableEdit` instance, no real
  Swing edit, no `Drawing` ever appears in the tests.
- `UndoRedoManager.undo()` reaches into the edit's `undo()`. Same:
  the edit is a mock; we `verify(edit, times(1)).undo()` and use
  `doAnswer` to simulate the re-entrant behaviour we want to pin.
- `CompositeEdit` reaches into `super.addEdit/undo/redo` (JDK
  `CompoundEdit`) and into its children's `addEdit/canUndo/undo/redo`.
  The children are Mockito mocks; the *one* place we cannot mock the
  collaborator is the nested-composite test, where the production code
  uses `instanceof CompositeEdit` — there I switched to `Mockito.spy`
  on a real `CompositeEdit` so the type-check passes *and* I can still
  `verify` calls on it.

Result: no test reaches into another production class beyond the unit
under test, and there is no Swing UI / `JFrame` / `ActionMap`
construction in the tests.

---

## 5. Java assertions for invariants ([Java Asserts])

Lab brief item 5 asks me to use Java `assert` statements for
invariants — *"something that should never happen"* — and notes that
unlike exceptions, an assertion is meant to **stop** the program, not
to flow control through.

I added **one** assertion to production code, in
`UndoRedoManager.runGuarded(Runnable)`:

```java
private void runGuarded(Runnable operation) {
    // Invariant: undo/redo is never re-entrant.
    assert !undoOrRedoInProgress : "runGuarded must not be called re-entrantly";
    undoOrRedoInProgress = true;
    try {
        operation.run();
    } finally {
        undoOrRedoInProgress = false;
        updateActions();
    }
}
```

**Why this invariant?** The whole reason `undoOrRedoInProgress` exists
is that listener fan-out during an undo would otherwise push events
back onto the stack. The manager's design assumes nesting
`runGuarded` calls is impossible. If it ever happened, the inner
finally block would clear the flag while the outer call is still
running — a silent corruption rather than a visible crash. Documenting
that as `assert !undoOrRedoInProgress` (a) tells the next reader the
invariant explicitly, and (b) makes it visibly fail the moment a
future change ever breaks it (running with `-ea`).

**Why an assertion and not an exception?** As the lab brief says:
this is a "should never happen". Production code throwing a runtime
exception there would be misleading — it would suggest the *user* did
something wrong, when in fact the *manager wiring* is bust. An
assertion is the right semantic: stop; this is a bug in the code, not
in the input.

I deliberately did *not* add the assertion to `addEdit` or
`setHasSignificantEdits`: those aren't invariants (the manager *does*
expect re-entrant `addEdit` calls during an undo — that's exactly the
contract the existing `if (undoOrRedoInProgress)` branch handles).

---

## 6. Things the tests revealed

### 6.1 A genuine architectural smell, not just a code smell

Building the test fixture surfaced a hidden cross-module dependency
that is invisible in the production source: the resource bundle
`org.jhotdraw.undo.Labels.properties` consumed by
`UndoRedoManager.getLabels()` is defined under
`jhotdraw-core/src/main/resources/`, even though the consuming class
lives in `jhotdraw-utils`. So **`jhotdraw-utils` cannot fully run
without `jhotdraw-core` on its classpath** — an inverted dependency
("utils depends on core") that breaks the obvious layering.

In the SOLID audit (`ActualizationLab1_Portfolio.md` §2.5), I had
already flagged `getLabels()` as a DIP weakness. The unit tests
escalated it from "could be cleaner" to "actually wrong": the
production code would throw `MissingResourceException` if you ever
deployed `jhotdraw-utils` standalone.

For this lab I worked around it by adding a test-classpath copy of
the bundle (see §2). A proper fix would be to move
`Labels.properties` into `jhotdraw-utils/src/main/resources/`. That
change crosses module boundaries and is out of scope here, but it is
now on the candidate list for a future change request.

### 6.2 An assumption about `editToBeUndone()`

My first draft of `addEdit_insignificant_doesNotMarkSignificant`
asserted `manager.canUndo()` after adding an insignificant edit.
That failed at runtime. Reading the JDK source revealed that
`UndoManager.editToBeUndone()` *deliberately skips insignificant
edits* — they are designed to ride along with the next significant
edit, so an insignificant edit on its own is "invisible" to the
manager from the user's point of view. The test was rewritten to
match, and the discovery made it into the test's Javadoc so the
next reader doesn't repeat the mistake.

### 6.3 The re-entrant guard is tested for the first time

Before this lab the only way to verify `undoOrRedoInProgress` worked
correctly was to launch a sample app, draw a figure, undo, and trust
nothing weird happened. After this lab it is pinned by
`addEditDuringUndo_isIgnoredAndEditIsDied` and
`undo_resetsGuardEvenWhenUndoableEditThrows`. These two tests
specifically protect the L3 `runGuarded` refactoring: any future
change that breaks the guard (e.g. an over-eager `try/with-resources`
rewrite, or moving the flag reset out of `finally`) fails the suite.

---

## 7. Verification

| Step | Command | Result |
|------|---------|--------|
| Per-module test | `mvn -pl jhotdraw-utils -am test` | **BUILD SUCCESS** — 28 tests, 0 failures, 0 errors, 0 skipped |
| Full reactor test | `mvn test` (12 modules) | **BUILD SUCCESS** — 28 new tests + 1 pre-existing `BezierPathNGTest` all pass |

---

## 8. Files added / changed (local working copy only)

| File | Change |
|------|--------|
| `jhotdraw-utils/pom.xml` | Added JUnit 4.13.2 + Mockito 4.11.0 test dependencies |
| `jhotdraw-utils/src/main/java/org/jhotdraw/undo/UndoRedoManager.java` | Added one Java `assert` invariant in `runGuarded` (3 lines incl. comment) |
| `jhotdraw-utils/src/test/java/org/jhotdraw/undo/UndoRedoManagerTest.java` | **new** — 16 tests |
| `jhotdraw-utils/src/test/java/org/jhotdraw/undo/CompositeEditTest.java` | **new** — 12 tests |
| `jhotdraw-utils/src/test/resources/org/jhotdraw/undo/Labels.properties` | **new** — test-classpath copy of the bundle |

No git commits. No push.

---

## 9. References

- [JUnit4] https://junit.org/junit4/
- [mockito.org] https://site.mockito.org/
- [JAVA Asserts] https://docs.oracle.com/javase/8/docs/technotes/guides/language/assert.html
- Local linked artifacts: `ChangeReqLab1_UserStory.md`, `CLLab1_ConceptLocation.md`, `AnalysisLab1_ImpactSet.md`, `RefactoringLab1_Portfolio.md`, `ActualizationLab1_Portfolio.md`.
