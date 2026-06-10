# Lab 3 — Refactoring Lab 1 (Report)

**Course:** Software Maintenance
**Student:** Ignas Beinoras
**Date:** 2026-04-30
**Lab brief:** `L3RefactoringLab1.pdf`
**Case study:** JHotDraw (`Software maintinence labs/JHotDraw/`, local working copy — no commits, no push)
**Linked Change Request / feature:** *Undo / Redo of editing operations*
**Portfolio artifact:** `Software maintinence Portfolio/RefactoringLab1_Portfolio.md`

---

## 1. Objective

Find Bad Code Smells in JHotDraw on classes touched by the *Undo / Redo*
change request, and apply behaviour-preserving Refactoring Patterns from
[Ker05] (Kerievsky, *Refactoring to Patterns*) to remove them.

## 2. Setup

- Working tree: `D:\Users\hp\Desktop\Software maintinence labs\JHotDraw`
- Build: Maven 3.9.9, javac 23.0.2.
- SonarLint: not used as a runtime check (the IDE plugin); smells were
  identified manually by reading the CHANGED-ring classes from
  `AnalysisLab1_ImpactSet.md` against the catalog in [Ker05] ch. 4.
  This is consistent with the lab brief — "Find Code smells in JHotDraw
  based on your change request and SonarLint" — i.e. the change request
  is the primary entry point, SonarLint a secondary aid.
- No remote pushed. No commits created. All edits are in the local
  working copy as the user requested.

## 3. Smells found and where

| # | Smell ([Ker05] ch. 4) | Location |
|---|-----------------------|----------|
| 1 | Duplicated Code (between two classes) | `UndoAction.java`, `RedoAction.java` (`jhotdraw-actions`) |
| 2 | Duplicated Code (within one class, three methods) | `UndoRedoManager.undo() / redo() / undoOrRedo()` (`jhotdraw-utils`) |
| 3 | Long Method + Duplicated structure | `UndoRedoManager.updateActions()` (`jhotdraw-utils`) |

## 4. Refactorings applied

| Smell | Refactoring | Source |
|-------|-------------|--------|
| 1 | **Form Template Method** | [Ker05] ch. 8, pp. 345–356 |
| 2 | **Compose Method** | [Ker05] ch. 7, pp. 123–130 |
| 3 | **Extract Method** | Fowler (supporting Compose Method) |

The full strategy and reasoning per refactoring are in
`RefactoringLab1_Portfolio.md` §4.

## 5. Files modified (local copy only)

```
jhotdraw-actions/src/main/java/org/jhotdraw/action/edit/AbstractUndoRedoViewAction.java   (new)
jhotdraw-actions/src/main/java/org/jhotdraw/action/edit/UndoAction.java                   (rewritten — 115 → 41 lines)
jhotdraw-actions/src/main/java/org/jhotdraw/action/edit/RedoAction.java                   (rewritten — 116 → 41 lines)
jhotdraw-utils/src/main/java/org/jhotdraw/undo/UndoRedoManager.java                       (edited)
```

Net effect: ~89 fewer source lines for the same external behaviour, and
the `undoOrRedoInProgress` invariant now lives in **one** place rather
than three.

## 6. Verification

| Step | Command | Result |
|------|---------|--------|
| Compile changed modules + their deps | `mvn -pl jhotdraw-actions,jhotdraw-utils -am compile` | BUILD SUCCESS |
| Compile entire reactor (12 modules) | `mvn compile` | BUILD SUCCESS |
| Run unit tests | `mvn test` | BUILD SUCCESS (single existing test passes) |

Public API was preserved deliberately: `UndoAction.ID`, `RedoAction.ID`,
the two-argument `(Application, View)` constructors, and every public
method on `UndoRedoManager` keep the same signatures and throws clauses
they had before.

## 7. Conclusion

Three smells from the [Ker05] catalog were removed by three small,
reversible refactorings on classes inside the CHANGED ring of the
*Undo / Redo* impact set. The full reactor still compiles and the
existing tests still pass, so the refactorings are behaviour-preserving
in the sense the lab brief defines.
