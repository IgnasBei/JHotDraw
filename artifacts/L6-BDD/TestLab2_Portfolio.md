# TestLab2 — Behaviour-Driven Testing of Undo/Redo (Portfolio Artifact)

**Course:** Software Maintenance
**Student:** Ignas Beinoras (GitHub: IgnasBeinoras)
**Case study:** JHotDraw — feature: *Undo / Redo of editing operations*
**Date:** 2026-05-28
**Lab brief:** `L6BDDLab.pdf` (TestLab2 — Behavior Driven Testing)
**Linked artifacts:** `ChangeReqLab1_UserStory.md`, `CLLab1_ConceptLocation.md`, `AnalysisLab1_ImpactSet.md`, `RefactoringLab1_Portfolio.md`, `ActualizationLab1_Portfolio.md`, `TestLab1_Portfolio.md`
**Local-only:** all paths refer to the local working copy at `Software maintinence labs/JHotDraw`. No commits, no push.

---

## 1. What the lab asks for

The brief gives three portfolio deliverables:

1. **Map the User Stories to BDD Given-When-Then scenarios.**
2. **Use [JGiven] to automate the BDD scenarios.**
3. **Use [AssertJ] for domain-specific assertions; for Swing applications use [AssertJ-Swing] to automate the scenarios.**

The feature under test is the same one I have carried through Labs 1–5:
the **Undo / Redo** of editing operations, whose user story and seven
acceptance criteria are already written up in `ChangeReqLab1_UserStory.md`.
This lab turns those acceptance criteria into executable BDD scenarios.

---

## 2. Mapping the user story to BDD scenarios

The user story (from `ChangeReqLab1_UserStory.md`):

> **As a** user editing a drawing in JHotDraw, **I want to** undo my last
> editing action and, if I change my mind, redo it, **so that** I can
> experiment freely and recover from mistakes without losing earlier work.

Each acceptance-criterion row maps to one Given-When-Then scenario. The
mapping is one-to-one so the traceability from story → criterion → executable
scenario is explicit:

| AC | Given | When | Then | Automated by |
|----|-------|------|------|--------------|
| AC1 | an open drawing **and** a significant edit "Move Figure" | the user invokes Undo | the most recent edit is reversed (and is now redoable) | JGiven `undo_reverses_the_most_recent_edit` |
| AC2 | an edit that has already been undone | the user invokes Redo | the edit is re-applied | JGiven `redo_reapplies_an_undone_edit` |
| AC3 | a freshly opened drawing | (—) | Undo and Redo are both disabled | JGiven `fresh_drawing_has_undo_and_redo_disabled` **+** AssertJ-Swing `fresh_drawing_disables_both_menu_items` |
| AC4 | an open drawing with a significant edit "Move Figure" | (—) | the Undo menu item is labelled "…Move Figure" | JGiven `undo_menu_item_shows_the_presentation_name` **+** AssertJ-Swing `a_significant_edit_enables_and_labels_the_undo_item` |
| AC5 | two independent drawings, each with its own edit | the user undoes in the first | the first is reversed; the second is unaffected | JGiven `undo_is_independent_per_view` |
| AC6 | an open drawing with a significant edit | (—) | the view is marked as having unsaved changes | JGiven `significant_edit_marks_unsaved_changes` (+ boundary `insignificant_edit_does_not_mark_unsaved_changes`) |
| AC7 | an edit that has been undone (redo available) | the user performs a new edit | the redo stack is cleared | JGiven `new_edit_after_undos_clears_redo_stack` |

The Given-When-Then prose is not just documentation — it is generated from
the JGiven step methods. The plain-text scenario report JGiven prints during
the build reads, for example:

```
 AC7: A new edit after undos clears the redo stack

   Given an open drawing
     And a significant edit named "Move Figure"
     And the edit has already been undone
    When performs a new "Draw Rectangle" edit
    Then the redo stack is cleared
```

---

## 3. Why these scenarios test the *behaviour*, not just the code

TestLab1 (L5) wrote **unit** tests: per-method, per-path, Mockito-isolated
tests of `UndoRedoManager` and `CompositeEdit`. This lab is deliberately at a
different level of abstraction. A BDD scenario describes a **user-visible
behaviour** ("undo reverses my last action") and is phrased in the domain
language of the user story, not in the language of the implementation. The
same domain class (`UndoRedoManager`) is exercised, but the *framing* is the
acceptance criterion, and the scenario name is the behaviour the user expects.

A few of the scenarios translate a GUI-level criterion into the closest
observable domain fact, and the mapping is recorded so it is honest:

- **AC1 "canvas is repainted"** → at the domain level this is represented by
  `editToBeUndone().undo()` being invoked exactly once and the edit moving to
  the redo stack. Repaint is a Swing side effect of the edit's own `undo()`.
- **AC3 / AC4 "Edit menu"** → covered twice: once at the domain level
  (the manager's `getUndoAction()`/`getRedoAction()` enabled-state and `NAME`)
  and once at the real-GUI level by the AssertJ-Swing scenario, which builds an
  actual `JMenuBar` and asserts on the live `JMenuItem`s.

---

## 4. Tooling and the JGiven stage structure

| Decision | Choice | Reason |
|----------|--------|--------|
| BDD framework | **JGiven 1.3.1** (`jgiven-junit`) | Last JGiven line that runs on Java 8 source/target (JHotDraw pins `1.8`). Integrates with the JUnit 4 already added in TestLab1. |
| Domain assertions | **AssertJ-Core 3.25.3** | The fluent `assertThat(...)` style the brief points to; Java-8 compatible. |
| Swing automation | **AssertJ-Swing 3.17.1** (`assertj-swing-junit`) | The brief's named tool for driving Swing scenarios. |
| Edit test doubles | **Mockito 4.11** (already present from L5) | The scenarios mock `UndoableEdit` collaborators, keeping the same single-method/single-path discipline as TestLab1. |

JGiven splits a scenario into three **stage** classes, which is the whole point
of the Given-When-Then separation:

- `GivenAnEditableDrawing` — arranges the manager(s) and the edits.
- `WhenTheUser` — models invoking Undo / Redo / a new edit.
- `ThenTheDrawing` — asserts the resulting state with **AssertJ** and verifies
  collaborator calls with Mockito.

State is shared between stages by **field name** (`@ScenarioState(resolution =
Resolution.NAME)`). This is a deliberate choice: the AC5 "two views" scenario
needs two `UndoRedoManager` fields (`manager`, `secondManager`) and two
`UndoableEdit` fields, which JGiven's default *type-based* injection could not
tell apart. Name-based resolution keys the shared state on the field name.

---

## 5. The AssertJ-Swing scenario (real GUI)

`UndoRedoMenuSwingScenarioTest` is the part that literally satisfies *"for
Swing applications use AssertJ-Swing to automate the scenarios."* It builds a
real `JFrame` whose Edit menu has two `JMenuItem`s backed by
`manager.getUndoAction()` / `getRedoAction()`, then drives it through an
AssertJ-Swing `FrameFixture`:

```java
window.menuItem("undoMenuItem").requireDisabled();          // AC3 fresh state
...
addEditOnEdt(MockEdits.significant("Move Figure"));          // a significant edit
window.menuItem("undoMenuItem").requireEnabled();            // AC1 Undo lights up
assertThat(undoMenuItemText()).isEqualTo("Undo Move Figure");// AC4 menu re-labels
```

Engineering notes that made it reliable:

- **All Swing state is touched on the EDT.** The frame is built inside a
  `GuiActionRunner.execute(GuiQuery)`, and every `addEdit`/`undo` goes through a
  `GuiActionRunner.execute(GuiTask)`. `FailOnThreadViolationRepaintManager` is
  installed so any accidental off-EDT access fails the test loudly.
- **No Robot interaction is used** — only `requireEnabled`/`requireDisabled`
  and a state read of the menu item's text. The scenario verifies that the
  `JMenuItem` text tracks the backing `Action`'s `NAME` (Swing's
  `configurePropertiesFromAction` wiring), which is exactly the AC4 behaviour.
- **Headless guard.** `Assume.assumeFalse(GraphicsEnvironment.isHeadless())`
  means the scenario runs on a desktop and skips cleanly on a headless CI box
  instead of erroring — so the suite stays portable.
- **`JMenuItemFixture` has no `requireText`**, so the menu label assertion is
  done by reading `target().getText()` on the EDT and asserting it with
  AssertJ-Core. This is the natural division the brief describes: AssertJ-Swing
  for the *widget state*, AssertJ for the *value*.

---

## 6. Build wiring (one new block + one surefire block)

`jhotdraw-utils/pom.xml`:

- Added three test-scope dependencies: `jgiven-junit`, `assertj-core`,
  `assertj-swing-junit`.
- Added a `maven-surefire-plugin` configuration with:

  ```
  --add-opens java.base/java.lang=ALL-UNNAMED -Dnet.bytebuddy.experimental=true
  ```

**Why the surefire argLine was necessary (a real discovery).** The build JDK
here is **Java 23**, while the project still compiles to Java 8 bytecode. JGiven
generates its stage proxy classes at runtime with ByteBuddy, using a
*reflection-based* class injector. On JDK 16+ that injector needs
`java.lang` opened, otherwise it fails with:

```
UnsupportedOperationException: Cannot define class using reflection:
Unable to make protected java.lang.Package java.lang.ClassLoader.getPackage(...)
accessible: module java.base does not "opens java.lang" to unnamed module
```

`--add-opens java.base/java.lang=ALL-UNNAMED` re-opens that package for the
test JVM, and `-Dnet.bytebuddy.experimental=true` lets the bundled ByteBuddy
accept the newer class-file version it sees under JDK 23. Both Mockito and
JGiven rely on ByteBuddy, so the one argLine covers both. This is purely a
*test-runtime* concern — no production code or compiler setting changed.

---

## 7. Test inventory (11 new tests; 39 in the module)

### `org.jhotdraw.undo.bdd.UndoRedoScenarioTest` — 8 JGiven scenarios
One per acceptance criterion (AC1–AC7) plus the AC6 insignificant-edit boundary.

### `org.jhotdraw.undo.bdd.UndoRedoMenuSwingScenarioTest` — 3 AssertJ-Swing scenarios
`fresh_drawing_disables_both_menu_items` (AC3),
`a_significant_edit_enables_and_labels_the_undo_item` (AC1+AC4),
`undoing_enables_the_redo_item` (AC1→AC2 menu transition).

### Supporting code
`GivenAnEditableDrawing`, `WhenTheUser`, `ThenTheDrawing` (JGiven stages) and
`MockEdits` (the `UndoableEdit` test-double factory).

Running the whole `jhotdraw-utils` module executes **39 tests** — the 28
TestLab1 unit tests + these 11 BDD scenarios — with **0 failures**.

---

## 8. Verification

| Step | Command | Result |
|------|---------|--------|
| JGiven scenarios only | `mvn -pl jhotdraw-utils test -Dtest=UndoRedoScenarioTest` | **BUILD SUCCESS** — 8/8 scenarios pass; Given-When-Then report printed |
| Per-module test | `mvn -pl jhotdraw-utils test` | **BUILD SUCCESS** — 39 tests, 0 failures, 0 errors, 0 skipped |
| Full reactor | `mvn test` (all 11 modules) | **BUILD SUCCESS** — no regressions anywhere |

JGiven writes a machine-readable scenario report to
`jhotdraw-utils/target/jgiven-reports/json/org.jhotdraw.undo.bdd.UndoRedoScenarioTest.json`
(an HTML report can be rendered from it with the JGiven report tool if needed).

---

## 9. Files added / changed (local working copy only)

| File | Change |
|------|--------|
| `jhotdraw-utils/pom.xml` | Added JGiven + AssertJ + AssertJ-Swing test deps and a surefire `--add-opens`/ByteBuddy argLine |
| `jhotdraw-utils/src/test/java/org/jhotdraw/undo/bdd/GivenAnEditableDrawing.java` | **new** — JGiven Given stage |
| `jhotdraw-utils/src/test/java/org/jhotdraw/undo/bdd/WhenTheUser.java` | **new** — JGiven When stage |
| `jhotdraw-utils/src/test/java/org/jhotdraw/undo/bdd/ThenTheDrawing.java` | **new** — JGiven Then stage (AssertJ assertions) |
| `jhotdraw-utils/src/test/java/org/jhotdraw/undo/bdd/UndoRedoScenarioTest.java` | **new** — 8 BDD scenarios (AC1–AC7 + boundary) |
| `jhotdraw-utils/src/test/java/org/jhotdraw/undo/bdd/UndoRedoMenuSwingScenarioTest.java` | **new** — 3 AssertJ-Swing GUI scenarios |
| `jhotdraw-utils/src/test/java/org/jhotdraw/undo/bdd/MockEdits.java` | **new** — `UndoableEdit` mock factory |

No production code changed. No git commits. No push.

---

## 10. References

- [JGiven] https://jgiven.org/
- [AssertJ] https://assertj.github.io/doc/
- [AssertJ-Swing] https://assertj.github.io/doc/#assertj-swing
- Local linked artifacts: `ChangeReqLab1_UserStory.md`, `TestLab1_Portfolio.md`.
