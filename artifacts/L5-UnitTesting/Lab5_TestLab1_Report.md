# Lab 5 — Test Lab 1 (Report)

**Course:** Software Maintenance
**Student:** Ignas Beinoras
**Date:** 2026-04-30
**Lab brief:** `L5TestLab1.pdf`
**Case study:** JHotDraw (`Software maintinence labs/JHotDraw/`, local working copy)
**Linked feature:** *Undo / Redo of editing operations*
**Portfolio artifact:** `Software maintinence Portfolio/TestLab1_Portfolio.md`

---

## 1. Objective

Per the lab brief: add JUnit 4 dependency to the relevant module(s),
write JUnit 4 unit tests for the most important domain logic methods of
the chosen feature (best case + boundary cases), apply Mockito to avoid
crossing method boundaries, and use Java `assert` statements for
invariants.

## 2. Targets

The two CHANGED-ring domain classes in `jhotdraw-utils` that the
Undo/Redo feature is built on:

- `org.jhotdraw.undo.UndoRedoManager` — undo stack, re-entrancy guard, dirty-state flag, menu actions.
- `org.jhotdraw.undo.CompositeEdit` — multi-edit aggregation with the `addEdit(this) ⇒ end()` protocol.

## 3. Tooling

- **JUnit 4.13.2** (matched to the existing version in `jhotdraw-samples-misc`).
- **Mockito 4.11.0** (Java-8-compatible — JHotDraw still pins source/target = 1.8).
- Surefire's TestNG provider runs JUnit 4 tests transparently, so no provider switch was needed.

## 4. Test inventory (28 tests, 0 failures)

| File | Tests | Notes |
|------|-------|-------|
| `jhotdraw-utils/.../undo/UndoRedoManagerTest.java` | 16 | 8 best-case + 5 boundary + 3 listener-wiring |
| `jhotdraw-utils/.../undo/CompositeEditTest.java` | 12 | 6 best-case + 6 boundary |

The headline boundary case is `addEditDuringUndo_isIgnoredAndEditIsDied`,
which pins the `undoOrRedoInProgress` invariant — the one the L3
`runGuarded` refactoring centralised.

## 5. Java assertion added to production code

One Java `assert` statement was added to
`UndoRedoManager.runGuarded(Runnable)` documenting the no-nested-undo
invariant:

```java
assert !undoOrRedoInProgress : "runGuarded must not be called re-entrantly";
```

This is exactly the kind of "should-never-happen" condition the lab
brief points to: a re-entrant call would silently corrupt the
`undoOrRedoInProgress` flag rather than visibly crash. The assertion
is documentation in code that fires under `-ea` (Maven Surefire's
default). Tests do not exercise the assertion itself (they exercise
the surrounding contract), but it is on the runtime invariant the
tests presuppose.

## 6. Files added / changed (local only)

```
jhotdraw-utils/pom.xml                                                   (added junit + mockito test deps)
jhotdraw-utils/src/main/java/org/jhotdraw/undo/UndoRedoManager.java      (added 1 assert)
jhotdraw-utils/src/test/java/org/jhotdraw/undo/UndoRedoManagerTest.java  (new — 16 tests)
jhotdraw-utils/src/test/java/org/jhotdraw/undo/CompositeEditTest.java    (new — 12 tests)
jhotdraw-utils/src/test/resources/org/jhotdraw/undo/Labels.properties    (new — test-classpath bundle copy)
```

## 7. Verification

| Command | Result |
|---------|--------|
| `mvn -pl jhotdraw-utils -am test` | BUILD SUCCESS — 28/28 pass |
| `mvn test` (full reactor, 12 modules) | BUILD SUCCESS — all existing tests still green |

## 8. Discoveries during testing

- **Hidden cross-module dependency.** The resource bundle
  `org.jhotdraw.undo.Labels.properties` lives under `jhotdraw-core`
  even though the consuming class is in `jhotdraw-utils`. So the
  manager *cannot* fully run with utils alone — a structural smell
  that escalates the DIP concern flagged in
  `ActualizationLab1_Portfolio.md` §2.5 from "could be cleaner" to
  "actually wrong".
- **`UndoManager.editToBeUndone()` skips insignificant edits.** First
  draft of one of the tests assumed insignificant edits surface in
  `canUndo()`; reading the JDK source corrected the test and the
  discovery is now in the test's Javadoc so the next reader doesn't
  repeat the mistake.

## 9. Conclusion

The Undo/Redo feature now has 28 unit tests pinning its core
invariants — including the re-entrancy guard centralised by the L3
refactoring and the `addEdit(this) ⇒ end()` protocol that
distinguishes JHotDraw's `CompositeEdit` from the JDK
`CompoundEdit` it extends. All tests pass; the rest of the reactor
remains green. Local-only, no commits, no push.
