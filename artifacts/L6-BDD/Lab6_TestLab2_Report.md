# Lab 6 — Test Lab 2: Behaviour-Driven Testing (Report)

**Course:** Software Maintenance
**Student:** Ignas Beinoras
**Date:** 2026-05-28
**Lab brief:** `L6BDDLab.pdf`
**Case study:** JHotDraw (`Software maintinence labs/JHotDraw/`, local working copy)
**Linked feature:** *Undo / Redo of editing operations*
**Portfolio artifact:** `Software maintinence Portfolio/TestLab2_Portfolio.md`

---

## 1. Objective

Per the lab brief: map the project's user stories to BDD Given-When-Then
scenarios, automate those scenarios with **JGiven**, use **AssertJ** for
domain assertions, and use **AssertJ-Swing** to automate the scenarios for the
Swing parts of the application.

The feature is the same one carried through Labs 1–5 — **Undo / Redo** — whose
user story and seven acceptance criteria already exist in
`ChangeReqLab1_UserStory.md`. This lab turns those criteria into executable
behaviour specifications.

## 2. Story → scenario mapping

Each acceptance criterion (AC1–AC7) became exactly one Given-When-Then
scenario, preserving traceability from user story → criterion → automated test:

- **AC1** Undo reverses the most recent edit.
- **AC2** Redo re-applies an undone edit.
- **AC3** A fresh drawing has Undo and Redo disabled.
- **AC4** The Undo menu item is labelled with the next edit's name.
- **AC5** Undo is independent per open view.
- **AC6** A significant edit marks the view as having unsaved changes
  (+ boundary: an insignificant edit does not).
- **AC7** A new edit after undos clears the redo stack.

The Given-When-Then text is generated from the JGiven step methods, so the
specification and the test are the same artifact.

## 3. Tooling

- **JGiven 1.3.1** (`jgiven-junit`) — BDD scenario automation, on top of the
  JUnit 4 added in TestLab1. Last line that supports the project's Java 8 target.
- **AssertJ-Core 3.25.3** — fluent domain assertions in the Then stage.
- **AssertJ-Swing 3.17.1** (`assertj-swing-junit`) — drives a real Swing Edit
  menu for the GUI-level scenarios.
- **Mockito 4.11** (already present) — `UndoableEdit` test doubles.

## 4. Structure

JGiven scenarios are split into three stage classes, mirroring Given/When/Then:

- `GivenAnEditableDrawing` — sets up the `UndoRedoManager`(s) and edits.
- `WhenTheUser` — invokes Undo / Redo / a new edit.
- `ThenTheDrawing` — AssertJ assertions on the resulting state; Mockito
  verification of collaborator calls.

Scenario state is shared by **field name** (`@ScenarioState(resolution =
Resolution.NAME)`) because the AC5 "two views" scenario needs two
`UndoRedoManager` fields, which JGiven's default type-based injection could not
disambiguate.

## 5. Test inventory (11 new tests, 0 failures)

| File | Tests | Notes |
|------|-------|-------|
| `.../undo/bdd/UndoRedoScenarioTest.java` | 8 | One JGiven scenario per AC1–AC7 + the AC6 insignificant-edit boundary |
| `.../undo/bdd/UndoRedoMenuSwingScenarioTest.java` | 3 | AssertJ-Swing scenarios on the live Edit menu (AC3, AC1+AC4, AC1→AC2) |

Supporting: JGiven stages `GivenAnEditableDrawing` / `WhenTheUser` /
`ThenTheDrawing`, and the `MockEdits` `UndoableEdit` factory.

Running the whole module executes **39 tests** (28 TestLab1 unit tests + 11 new
BDD scenarios).

## 6. AssertJ-Swing scenario

`UndoRedoMenuSwingScenarioTest` builds a real `JFrame` + `JMenuBar` whose
Undo/Redo `JMenuItem`s are backed by the manager's `Action`s, then asserts via
an AssertJ-Swing `FrameFixture` that the menu reflects manager state
(`requireDisabled`/`requireEnabled`, and the menu label tracking the action's
`NAME`). All Swing access is on the EDT (`GuiActionRunner`,
`FailOnThreadViolationRepaintManager`); the scenario is guarded by
`Assume.assumeFalse(GraphicsEnvironment.isHeadless())` so it runs on a desktop
and skips on headless CI rather than failing.

## 7. Build change and the JDK-23 discovery

`jhotdraw-utils/pom.xml` gained the three test dependencies and a
`maven-surefire-plugin` argLine:

```
--add-opens java.base/java.lang=ALL-UNNAMED -Dnet.bytebuddy.experimental=true
```

This was **required, not cosmetic**. The build JDK is Java 23. JGiven generates
its stage proxies with ByteBuddy's reflection-based class injector, which on
JDK 16+ fails unless `java.lang` is opened:

```
Cannot define class using reflection: ... module java.base does not
"opens java.lang" to unnamed module
```

`--add-opens` fixes that; `net.bytebuddy.experimental=true` lets the bundled
ByteBuddy accept the JDK-23 class-file version. Both Mockito and JGiven use
ByteBuddy, so one argLine covers both. No production code or compiler setting
changed — this is a test-runtime concern only.

## 8. Verification

| Command | Result |
|---------|--------|
| `mvn -pl jhotdraw-utils test -Dtest=UndoRedoScenarioTest` | BUILD SUCCESS — 8/8 JGiven scenarios pass |
| `mvn -pl jhotdraw-utils test` | BUILD SUCCESS — 39 tests, 0 failures |
| `mvn test` (full reactor, 11 modules) | BUILD SUCCESS — no regressions |

JGiven writes a scenario report to
`jhotdraw-utils/target/jgiven-reports/json/`.

## 9. Conclusion

The Undo/Redo user story is now backed by executable BDD specifications: eight
JGiven scenarios pin the domain behaviour (one per acceptance criterion, with
AssertJ assertions and Mockito-isolated collaborators), and three AssertJ-Swing
scenarios verify the same behaviour at the live Edit-menu level. Together with
the 28 TestLab1 unit tests, the feature is covered at both the unit and the
behaviour level. All tests pass; the full reactor remains green. Local-only, no
commits, no push.
