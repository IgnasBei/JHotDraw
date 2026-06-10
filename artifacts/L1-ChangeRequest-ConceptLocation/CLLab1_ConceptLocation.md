# CLLab — Concept Location (Portfolio Artifact)

**Course:** Software Maintenance
**Student:** Ignas Beinoras (GitHub: IgnasBeinoras)
**Case study:** JHotDraw (working locally — no remote push for this lab)
**Date:** 2026-04-27
**Linked Change Request / User Story:** *Undo / Redo of editing operations* (see `ChangeReqLab1_UserStory.md`)

---

## How concept location was performed

Per the lab brief, concept location was started from a controller class and
followed at runtime through the IDE debugger. Concretely:

1. **Entry point chosen:** `org.jhotdraw.action.edit.UndoAction#actionPerformed`
   — invoked from the *Edit → Undo* menu item in the Draw sample
   (`org.jhotdraw.samples.draw.Main`).
2. **Breakpoints set** at: `UndoAction.actionPerformed`, `RedoAction.actionPerformed`,
   `UndoRedoManager.undo`, `UndoRedoManager.addEdit`, and
   `AbstractDrawing.fireUndoableEditHappened`.
3. **Scenario exercised in the debugger:**
   a. Launched the Draw sample, drew one rectangle, moved it, then changed its
      fill colour. Three edits expected on the stack.
   b. Stepped from the Edit menu click into `UndoAction.actionPerformed`, observed
      it delegate to the active view's action map under id `"edit.undo"`.
   c. Stepped into `UndoRedoManager$UndoAction.actionPerformed → undo()` — saw
      the top `UndoableEdit` popped and `editToBeUndone()` change.
   d. Returned to drawing a new figure to observe `addEdit` being called from
      `Drawing.fireUndoableEditHappened`, with the Drawing (model) acting as
      `UndoableEditListener` source and the `UndoRedoManager` as listener.
   e. Inspected `CompositeEdit` while moving multiple selected figures to confirm
      multi-figure transforms are recorded as a single undo step via
      `TransformEdit` instances bundled into one composite.
4. **Filter:** non-domain wiring (`AbstractAction` plumbing, Swing PropertyChange
   bookkeeping, resource bundles) was excluded from the final domain list as
   suggested by the lab brief ("localize only the domain classes that are
   related to the domain concepts").

### Modules touched

```
jhotdraw-actions  → controller actions on the menu
jhotdraw-utils    → undo manager + composite edit
jhotdraw-core     → drawing model + concrete UndoableEdits + view
jhotdraw-samples  → DrawView wires the manager into the view's action map
```

---

## Initial Set of Domain Classes

The table below is the **deliverable** for CLLab1. Source paths are given
relative to the JHotDraw repository root for traceability.

| # | Domain Class | Responsibility |
|---|--------------|----------------|
| 1 | `org.jhotdraw.action.edit.UndoAction` <br/>(`jhotdraw-actions/.../action/edit/UndoAction.java`) | Application-level controller for the *Edit → Undo* menu item. Looks up the active view, finds the view-specific undo action under id `"edit.undo"` in its action map, and delegates `actionPerformed` to it. Tracks enabled/label state via PropertyChangeListener. |
| 2 | `org.jhotdraw.action.edit.RedoAction` <br/>(`jhotdraw-actions/.../action/edit/RedoAction.java`) | Counterpart to `UndoAction` for the *Edit → Redo* menu item. Same delegation pattern through `View.getActionMap().get("edit.redo")`. |
| 3 | `org.jhotdraw.action.AbstractViewAction` <br/>(`jhotdraw-actions/.../action/AbstractViewAction.java`) | Base class for view-scoped menu actions. Supplies `getActiveView()` and the install/uninstall lifecycle that `UndoAction`/`RedoAction` use to follow view focus changes. |
| 4 | `org.jhotdraw.samples.draw.DrawView` <br/>(`jhotdraw-samples/.../samples/draw/DrawView.java`) | Per-view glue. Owns the per-view `UndoRedoManager`, registers it as an `UndoableEditListener` on the drawing model, and publishes the manager's undo/redo `Action`s into the view's action map under `UndoAction.ID` / `RedoAction.ID`. Also propagates `hasSignificantEdits` to `setHasUnsavedChanges`. (Equivalent classes exist in the SVG, ODG, Net, Pert, Teddy samples.) |
| 5 | `org.jhotdraw.undo.UndoRedoManager` <br/>(`jhotdraw-utils/.../undo/UndoRedoManager.java`) | **Core domain class.** Extends `javax.swing.undo.UndoManager`. Holds the linear stack of `UndoableEdit`s, exposes `getUndoAction()` / `getRedoAction()` (the inner Swing actions actually placed in the view's action map), guards against re-entrancy with `undoOrRedoInProgress`, tracks `hasSignificantEdits`, and updates the menu items' labels to the localised presentation names. |
| 6 | `org.jhotdraw.undo.CompositeEdit` <br/>(`jhotdraw-utils/.../undo/CompositeEdit.java`) | Aggregates several `UndoableEdit`s into one undoable step (e.g. a multi-figure move). Used by tools and actions so the user perceives one undo per gesture rather than one per figure. |
| 7 | `org.jhotdraw.draw.Drawing` (interface) and `org.jhotdraw.draw.AbstractDrawing` (default impl) <br/>(`jhotdraw-core/.../draw/AbstractDrawing.java`) | The drawing model — the *source* of undoable edits. Implements `add/removeUndoableEditListener` and `fireUndoableEditHappened(UndoableEdit)`, which is what the manager subscribes to. Every figure mutation that should be undoable ultimately goes through this fire method. |
| 8 | `javax.swing.undo.UndoableEdit` / `AbstractUndoableEdit` (JDK) | The contract for a single reversible operation: `undo()`, `redo()`, `canUndo()`, `canRedo()`, `isSignificant()`, `getPresentationName()`. JHotDraw's concrete edits implement this contract; the manager only sees this interface. Listed because the feature's correctness depends on each concrete edit honouring it (especially `isSignificant`). |
| 9 | `org.jhotdraw.draw.event.TransformEdit` <br/>(`jhotdraw-core/.../draw/event/TransformEdit.java`) | Representative concrete `AbstractUndoableEdit` — captures a figure's affine transform so it can be inverted. Chosen as the example of how domain-level undoable edits are produced (figure moves, alignments, rotations); analogous edit classes exist for attribute changes, additions, and deletions. |

---

## Notes on the result

- The **controller surface** is small (rows 1–3) — two menu actions plus a base
  class. This matches the lab brief's hint that "features are often started
  from the controller classes".
- The **domain core** is row 5 (`UndoRedoManager`) plus row 7 (`Drawing` /
  `AbstractDrawing`). These two classes carry the feature's invariants
  (linear stack, listener wiring, significant-edit tracking).
- Rows 6, 8, 9 are the **data** of the feature — the edit objects that flow
  between model and manager.
- Tooling/Swing-only classes seen during debugging (`PropertyChangeSupport`,
  `ResourceBundleUtil`, `AbstractAction`) were intentionally excluded — they
  are infrastructure, not domain.
- This list is the *initial* set, as the brief specifies. A full impact analysis
  would also pull in every concrete `UndoableEdit` subclass scattered through
  `org.jhotdraw.draw.action.*` (e.g. `AlignAction`, `AttributeAction`,
  `ApplyAttributesAction`) — those are the call-sites of
  `fireUndoableEditHappened`, but they are *producers* of the feature's input
  rather than parts of the feature itself.
