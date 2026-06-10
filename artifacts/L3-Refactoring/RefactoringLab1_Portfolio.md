# RefactoringLab — Refactoring of the Undo/Redo Feature (Portfolio Artifact)

**Course:** Software Maintenance
**Student:** Ignas Beinoras (GitHub: IgnasBeinoras)
**Case study:** JHotDraw (working locally — no remote push for this lab)
**Date:** 2026-04-30
**Linked Change Request:** *Undo / Redo of editing operations* (`ChangeReqLab1_UserStory.md`)
**Linked Concept Location:** `CLLab1_ConceptLocation.md`
**Linked Impact Set:** `AnalysisLab1_ImpactSet.md`

---

## 1. Why these classes were chosen

The classes I refactored are inside the **CHANGED** ring of the impact set
(`AnalysisLab1_ImpactSet.md`):

| File | Module | Role for the feature |
|------|--------|----------------------|
| `org.jhotdraw.action.edit.UndoAction` | jhotdraw-actions | Application-level *Edit → Undo* menu controller |
| `org.jhotdraw.action.edit.RedoAction` | jhotdraw-actions | Application-level *Edit → Redo* menu controller |
| `org.jhotdraw.undo.UndoRedoManager` | jhotdraw-utils | Per-view undo stack, owner of the inner Swing actions |

Concept location and impact analysis already pointed to these as the spine
of the feature — and they are exactly the spots where the smells from
Kerievsky [Ker05] chapter 4 are concentrated.

---

## 2. Code smells I identified ([Ker05] ch. 4)

### 2.1 Duplicated Code — between `UndoAction` and `RedoAction`

`UndoAction.java` and `RedoAction.java` (115/116 lines each) are *near-clones*.
They share, byte-for-byte modulo identifier renames:

- the same `redoActionPropertyListener` (anonymous `PropertyChangeListener`)
- the same `updateEnabledState()`
- the same `updateView(View, View)`
- the same `installViewListeners(View)` / `uninstallViewListeners(View)`
- the same `actionPerformed(ActionEvent)`
- the same private `getRealRedoAction()` / `getRealUndoAction()`

The only differences are the action ID (`"edit.undo"` vs `"edit.redo"`) and a
local variable name. This is the textbook **Duplicated Code** smell ([Ker05]
§4.1): two places will drift over time, and a fix in one will silently miss
the other.

### 2.2 Duplicated Code — `UndoRedoManager.undo() / redo() / undoOrRedo()`

All three methods wrap a `super.*` call in the *same* try/finally guard:

```java
undoOrRedoInProgress = true;
try { super.undo(); }       // resp. super.redo() / super.undoOrRedo()
finally {
    undoOrRedoInProgress = false;
    updateActions();
}
```

The guard is non-trivial — it is the invariant that prevents listener fan-out
during an undo from pushing events back onto the stack. Three copies of an
invariant-bearing block is a maintenance hazard.

### 2.3 Long Method / Duplicated structure — `UndoRedoManager.updateActions()`

`updateActions()` mixed two duplicated branches (one for undo, one for redo)
plus a debug log block, doing four jobs in 25+ lines. This is a mild
**Long Method** smell ([Ker05] §4.2) sitting on top of a Duplicated Code
substructure.

### 2.4 Other smells noted but **not** addressed in this lab

These were spotted while reading; they are listed for transparency but I
intentionally left them out of scope to keep each refactoring small and
behaviour-preserving (the lab brief explicitly favours small steps).

- **Indecent Exposure** ([Ker05] §4.5): `UndoRedoManager.DEBUG` is a public
  source-level constant (well, `private static final` here, but the
  pattern of `if (DEBUG) System.out.println(...)` should be a `Logger`).
- **Primitive Obsession**: `editToBeUndone() == anEdit` identity check in
  `addEdit` is brittle; would benefit from a tiny helper.
- **Switch Statements** at module boundary: each sample's `*View` repeats
  the same wiring (`UndoRedoManager` → `ActionMap` keys). A
  `UndoRedoSupport` extract class would eliminate it across the five
  sibling sample views — but that crosses module boundaries and is too
  large for this lab.

---

## 3. Strategy

The lab brief describes refactoring as a "series of small, behaviour-preserving
transformations". I followed that literally:

1. Pick the smell with the highest payoff vs. risk: **Duplicated Code between
   `UndoAction` and `RedoAction`**. The two classes are public API on the
   `jhotdraw-actions` module and used by every sample view, so collapsing
   their duplication has reach but is *type-stable*: the public class names,
   IDs, and constructors stay identical, so callers never know.
2. Move down the impact ring to **`UndoRedoManager`**, which is internal to
   `jhotdraw-utils` and only its public methods (`undo`, `redo`,
   `undoOrRedo`, `updateActions` is private) are observed by callers.
   Apply two more local refactorings inside it.
3. After each refactoring run `mvn compile` on the affected module **and** on
   the whole project (12 modules) so any sample breakage shows up
   immediately. Keep going only when SUCCESS is green.

I deliberately did **not** combine the three refactorings into one edit:
each is a separate, reversible step. If the build had broken on step 2 I
would still have the gain from step 1.

---

## 4. Refactorings applied (from [Ker05])

### R1 — **Form Template Method** ([Ker05] ch. 8, pp. 345–356)

> *"You have two methods in subclasses that perform similar steps in the
> same order, yet the steps are different. Form a template method that
> calls the steps in order, leaving the subclasses to override the
> different ones."*

- **Where:** `org.jhotdraw.action.edit.UndoAction` and `RedoAction`.
- **What I did:** Introduced a new package-private abstract base class
  `AbstractUndoRedoViewAction extends AbstractViewAction`. Pulled
  `updateEnabledState`, `updateView`, `installViewListeners`,
  `uninstallViewListeners`, `actionPerformed`, the `realActionListener`
  field, the `labels` field, and the private `getRealAction()` helper
  into the base class. Left exactly one abstract step:
  ```java
  protected abstract String getActionId();
  ```
  Subclasses now declare *only* their `ID` constant, their constructor
  signature, and a one-line override of `getActionId()`.
- **Reasoning:** This is the canonical [Ker05] move when "the same algorithm
  appears in two places, varying only in a single value". The id *is*
  the only varying step. Generalising it via a Template Method is more
  honest than parameterising via a constructor argument because:
  - The id is a class-level identity (it's also the public `ID` constant)
    so it belongs in the type, not as data.
  - `Action`s are constructed by reflection / via class names elsewhere
    in the JHotDraw `ApplicationModel`; preserving the public class names
    avoids breaking `ApplicationModel.initApplication`.
- **Result:** `UndoAction` shrank from 115 → 41 lines. `RedoAction` shrank
  from 116 → 41 lines. ~140 lines of duplicated code collapsed into one
  ~95-line base class. Behaviour preserved: the ids, the listener
  semantics, and the public constructors are unchanged.

### R2 — **Compose Method** ([Ker05] ch. 7, pp. 123–130)

> *"You want code to be so easy to follow it reads like a well-written
> paragraph. Transform the logic into a small number of intention-revealing
> steps at the same level of detail."*

- **Where:** `UndoRedoManager.undo()`, `redo()`, `undoOrRedo()`.
- **What I did:** Extracted the shared `undoOrRedoInProgress` guard into a
  private helper:
  ```java
  private void runGuarded(Runnable operation) {
      undoOrRedoInProgress = true;
      try { operation.run(); }
      finally {
          undoOrRedoInProgress = false;
          updateActions();
      }
  }
  ```
  Each of the three public methods is now a single intention-revealing
  line: `runGuarded(super::undo);`. The unchecked
  `CannotUndoException` / `CannotRedoException` (both `RuntimeException`)
  propagate through the `Runnable` cleanly, so the throws clauses are
  preserved.
- **Reasoning:** The guard is the invariant; the three methods are
  *intentions*. Compose Method separates "what" from "how" in exactly the
  way [Ker05] describes. As a bonus, future undo-like operations only need
  one line at the call site to inherit the same invariant — adding a
  fourth one cannot accidentally ship without the guard.

### R3 — **Extract Method** (Fowler, supporting the Compose Method strategy)

- **Where:** `UndoRedoManager.updateActions()`.
- **What I did:** Replaced two near-identical 6-line branches with two
  calls to a new private helper:
  ```java
  configureMenuAction(undoAction, canUndo(), getUndoPresentationName(), "edit.undo.text");
  configureMenuAction(redoAction, canRedo(), getRedoPresentationName(), "edit.redo.text");
  ```
  `configureMenuAction` does the enable + label + tooltip on any
  `AbstractAction`. `updateActions` itself is now four lines (plus the
  debug-log block, deliberately untouched).
- **Reasoning:** This is a supporting move — Fowler-level Extract Method
  rather than a pattern-shaped refactoring. I list it because [Ker05]
  treats Compose Method as a *consequence* of repeated Extract Method,
  and this is exactly that: the helper makes `updateActions()` itself
  read as one paragraph.

---

## 5. Verification (behaviour preservation)

| Step | Command | Result |
|------|---------|--------|
| 1 | `mvn -pl jhotdraw-actions,jhotdraw-utils -am compile` | **BUILD SUCCESS** |
| 2 | `mvn compile` (all 12 modules: api, utils, xml, datatransfer, actions, core, gui, app, samples, samples-misc, samples-mini) | **BUILD SUCCESS** |
| 3 | `mvn test` (all modules — `BezierPathNGTest` is the only existing unit test) | **BUILD SUCCESS** |

Public API surface is unchanged:

- `UndoAction.ID` and `RedoAction.ID` keep the same values (`"edit.undo"`, `"edit.redo"`).
- The two-argument `(Application, View)` public constructors keep the same signatures.
- `UndoRedoManager#undo() / redo() / undoOrRedo() / addEdit / getUndoAction / getRedoAction` keep the same signatures and the same throws clauses.
- The private `updateActions()` keeps the same observable side effects (enabled state, `Action.NAME`, `Action.SHORT_DESCRIPTION`).

So the whole change is purely *internal restructuring* — exactly what
the lab brief defines refactoring to be.

---

## 6. Files changed (local working copy only — not pushed)

| File | Change | Net lines |
|------|--------|-----------|
| `jhotdraw-actions/.../action/edit/AbstractUndoRedoViewAction.java` | **new** (base class) | +96 |
| `jhotdraw-actions/.../action/edit/UndoAction.java` | rewritten (slim subclass) | −74 |
| `jhotdraw-actions/.../action/edit/RedoAction.java` | rewritten (slim subclass) | −75 |
| `jhotdraw-utils/.../undo/UndoRedoManager.java` | edited in place | −36 lines net |

Net: ~ **−89 lines** for the same external behaviour, and the central
`undoOrRedoInProgress` invariant now exists in **one** place instead of three.

---

## 7. Reflection

The most striking finding from this lab is how cleanly the smells line up
with the impact analysis. The three CHANGED-ring controller / manager
classes were *also* the three classes carrying the worst Duplicated Code,
which makes sense in hindsight: they are the spots a maintainer has to
edit every time the feature contract shifts, so they are the spots where
copy-paste accumulates.

Form Template Method was the highest-payoff refactoring by an order of
magnitude — collapsing a public 115-line file and another 116-line file
into a 96-line base + two 41-line subclasses. The two `UndoRedoManager`
refactorings are smaller but more *structural*: they turn a duplicated
invariant (the `undoOrRedoInProgress` guard) into a single named concept
(`runGuarded`), so future undo-like operations cannot ship without it.

If I were to keep going, the next move would be to **Extract Class** the
per-view wiring that every sample's `*View` repeats (DrawView, SVGView,
ODGView, NetView, PertView, TeddyView) into a single
`UndoRedoSupport` helper. That, however, crosses the boundary out of the
CHANGED ring into the PROPAGATES ring of `AnalysisLab1_ImpactSet.md`, so
it is a Lab-2-sized change rather than a Lab-1 step.

---

## 8. References

- [Ker05] Kerievsky, J. *Refactoring to Patterns*, Addison-Wesley, 2005.
  - Ch. 4 — *Code Smells*.
  - Ch. 7 — *Simplification* (Compose Method, pp. 123–130).
  - Ch. 8 — *Generalization* (Form Template Method, pp. 345–356).
- Fowler, M. *Refactoring: Improving the Design of Existing Code* — Extract Method.
- http://refactoring.com/catalog/ (linked from the lab brief).
