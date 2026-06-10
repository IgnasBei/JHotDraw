# Lab 2 Report — AnalysisLab (Impact Analysis)

**Course:** Software Maintenance
**Student:** Ignas Beinoras (GitHub: IgnasBeinoras)
**Working solo** — no team coordination.
**Date:** 2026-04-28
**Local working copy:** `D:\Users\hp\Desktop\Software maintinence labs\JHotDraw`
**Course base repo:** `sweat-tek/JHotDraw`

> Per assignment: nothing was committed or pushed for this lab. All artifacts
> are kept locally. Portfolio deliverable is in
> `D:\Users\hp\Desktop\Software maintinence Portfolio\AnalysisLab1_ImpactSet.md`.

---

## 1. Objective

Apply the impact-analysis algorithm in **Figure 7.9** of [Raj13] (static + dynamic analysis) to the change request from Lab 1 (*Undo / Redo of editing operations*) in order to produce an **estimated impact set** of classes — i.e. every class that is either modified, propagates the change, or sits on the boundary of the closure.

## 2. Inputs to this lab

- **Change request / user story:** `ChangeReqLab1_UserStory.md`. *As a JHotDraw user, I want to undo my last editing action and (if I change my mind) redo it.*
- **Concept-location result (initial set):** the 9-row table in `CLLab1_ConceptLocation.md`. The seed for impact analysis is `org.jhotdraw.undo.UndoRedoManager` (the core domain class).

## 3. Method

The algorithm in Figure 7.9 was executed manually with these aids:

- **Static analysis.** Read every class in the initial set and walked its `import` list to enumerate first-degree neighbours. Used `grep` over the JHotDraw tree for the three "spreader" patterns:
  - `fireUndoableEditHappened` → 63 source files (every producer of an `UndoableEdit` from a `Drawing`).
  - `extends AbstractUndoableEdit` → 8 source files (every concrete `UndoableEdit` subclass in JHotDraw).
  - `UndoRedoManager` → 14 source files (every per-view consumer of the manager).
  - `CompositeEdit` → 13 source files (callers that bundle multiple edits).
- **Dynamic analysis.** Replayed the debugger trace from CLLab1: drew a rectangle, moved it, recoloured it in the Draw sample, then exercised Undo and Redo with breakpoints on `UndoAction.actionPerformed`, `UndoRedoManager.undo`, `UndoRedoManager.addEdit`, `AbstractDrawing.fireUndoableEditHappened`. The trace confirmed the *runtime* fan-in (tools/handles/actions firing edits) and fan-out (manager invoking each `UndoableEdit.undo()`) that the static graph showed.

The algorithm itself ran as:

```
1. Build the interaction diagram. Mark every reachable class BLANK.
2. Mark UndoRedoManager (concept-located seed) CHANGED.
3. While any class is NEXT:
     for each CHANGED class C:
       for each BLANK neighbour N of C:  mark N as NEXT
     pick a NEXT class M and reclassify it:
       UNCHANGED  -> change does not affect M.
       PROPAGATES -> M is a path the change travels through; not modified, must be re-verified.
       CHANGED    -> M will be modified.
4. Stop when no class is NEXT (all reachable classes are UNCHANGED / PROPAGATES / CHANGED).
```

## 4. Walk — iteration log (abridged)

**Iter 0 — seed.** `UndoRedoManager` → CHANGED.

**Iter 1 — neighbours of `UndoRedoManager`.**
- `UndoManager`, `UndoableEdit`, `AbstractUndoableEdit`, `CompoundEdit`, `CannotUndoException`, `CannotRedoException`, `AbstractAction`, `PropertyChangeSupport` (JDK) → UNCHANGED.
- `ResourceBundleUtil` → UNCHANGED (only properties keys may grow).
- `org.jhotdraw.action.edit.UndoAction`, `RedoAction` → CHANGED (in CL set; controllers).
- `AbstractDrawing` → CHANGED (in CL set; the source of edits the manager listens to).
- `CompositeEdit` → CHANGED (in CL set; aggregator).

**Iter 2 — neighbours of the new CHANGED frontier.**
- `AbstractViewAction` → CHANGED (parent of UndoAction/RedoAction; CL set).
- `Application` → UNCHANGED. `View` → PROPAGATES (concrete views must publish the UndoAction/RedoAction IDs in their action maps).
- `Drawing` (interface) → CHANGED (defines `fireUndoableEditHappened` contract — in CL set).
- `Figure`, `CompositeFigure` → PROPAGATES (figure mutations are the producer events).
- `AbstractAttributedCompositeFigure` → UNCHANGED (parent of `AbstractDrawing`, no undo wiring).
- `UndoableEditListener`, `UndoableEditEvent` → UNCHANGED (JDK).

**Iter 3 — neighbours of `AbstractViewAction`, `Drawing`, `CompositeEdit`, `TransformEdit`.**
- `WeakPropertyChangeListener` → UNCHANGED.
- `InputFormat`, `OutputFormat` → UNCHANGED (file-IO, orthogonal to undo).
- All concrete `AbstractUndoableEdit` subclasses (`TransformRestoreEdit`, `SetBoundsEdit`, `BezierNodeEdit`, `AttributeChangeEdit`, `CompositeFigureEdit`, `NonUndoableEdit`, `PropertyChangeEdit`) → PROPAGATES.

**Iter 4 — fan-out via `fireUndoableEditHappened`.**
- All 8 `org.jhotdraw.draw.tool.*` classes → PROPAGATES.
- All 13 `org.jhotdraw.draw.handle.*` classes → PROPAGATES.
- All 16 `org.jhotdraw.draw.action.*` classes → PROPAGATES.
- `DefaultDrawingViewTransferHandler`, `DefaultDrawingView`, `AbstractDrawingView`, `GraphicalCompositeFigure` → PROPAGATES.
- `FontChooserHandler`, `EditCanvasPanel` (gui) → PROPAGATES.
- All 7 figure classes (`AbstractFigure`, `AbstractCompositeFigure`, `BezierFigure`, `LineFigure`, `LineConnectionFigure`, `LabeledLineConnectionFigure`, `QuadTreeCompositeFigure`) → PROPAGATES.

**Iter 5 — sibling `*View` consumers of `UndoRedoManager`.**
- Draw sample: `DrawView` (CHANGED, in CL set), `DrawingPanel` → PROPAGATES.
- SVG sample: `SVGView`, `SVGDrawingPanel`, `ActionsToolBar`, `SVGPathFigure`, `SVGImageFigure`, `SVGAttributedFigure`, `SVGBezierFigure`, `SVGRectRadiusHandle`, `CombineAction` → PROPAGATES.
- ODG sample: `ODGView`, `ODGDrawingPanel`, `ODGPathFigure`, `ODGAttributedFigure`, `ODGBezierFigure`, `ODGRectRadiusHandle` → PROPAGATES.
- Net sample: `NetView`, `NetPanel` → PROPAGATES.
- Pert sample: `PertView`, `PertPanel` → PROPAGATES.
- Teddy sample (text editor): `TeddyView`, `FindDialog` → PROPAGATES (`FindDialog` uses `CompositeEdit`).

**Iter 6 — termination.** All BLANK neighbours of the PROPAGATES frontier are either already classified or stable JDK / utility classes. No NEXT remains. Walk terminates.

## 5. Result

- **CHANGED:** 9 classes — exactly the CL set from Lab 1 (controllers, view glue, manager + composite, drawing model, representative concrete edit).
- **PROPAGATES:** ~80 classes — concrete `UndoableEdit` subclasses + every tool/handle/draw-action that fires edits + sibling sample views.
- **UNCHANGED:** ~10 classes (project utilities + JDK contract).

The full breakdown is in **Table 1** of `AnalysisLab1_ImpactSet.md` (24 rows, by package, with comments).

### Key observations

- The feature's "core" is small (≈ 14 classes across `org.jhotdraw.action.edit`, `org.jhotdraw.action`, `org.jhotdraw.undo`, `org.jhotdraw.draw` Drawing/AbstractDrawing, `org.jhotdraw.draw.event.TransformEdit`, `org.jhotdraw.samples.draw.DrawView`).
- The feature's *blast radius* is very wide (≈ 90 classes) because Undo/Redo is **event-driven** and every figure mutation in JHotDraw is a producer.
- The most fragile invariant in the impact set is **significance** (`isSignificant`, `hasSignificantEdits`). A change to the manager's significance rule is invisible by static analysis but propagates to every concrete edit — so any change to the `UndoRedoManager` contract will need re-verification across `org.jhotdraw.draw.event.*`, the tool package, and the handle package.
- Sibling samples (SVG, ODG, Net, Pert, Teddy) all share the same per-view wiring pattern. A code-level change in `UndoRedoManager`'s public surface multiplies into 5 samples, even though the *user story* is bound to the Draw sample.

## 6. Deviations / honesty notes

- The "interaction diagram" called for by step 1 is implicit here — the textual description in `CLLab1_ConceptLocation.md` plus the import-graph traversal above plays the role of the diagram. Drawing it in UML is not the deliverable the lab asks for; the deliverable is the impact set + Table 1.
- `org.jhotdraw.draw.action.*` is counted at 16 because that is the number of classes I traversed; the package contains a few more (`InputFormatAction`, `OutputFormatAction`, etc.) that are **not** undo-touching and were correctly *not* visited (BLANK). Those would be UNCHANGED if the walk were continued.
- **JDK classes** are listed as the boundary of the closure, not as part of the project impact set. They are UNCHANGED because we cannot modify them; the comment is to make the boundary explicit.

## 7. Files produced

| Path | Type |
|------|------|
| `Software maintinence Portfolio/AnalysisLab1_ImpactSet.md` | Portfolio artifact (Table 1 + algorithm transcript) |
| `Software maintinence reports/Lab2_AnalysisLab1_Report.md` | This report |

Nothing was modified inside the JHotDraw working copy and nothing was pushed to GitHub.

## 8. Anticipated next lab

The natural follow-up is the *change-implementation* lab. The CHANGED rows above (9 classes) are the implementation surface; the PROPAGATES rows define the **regression test scope** — that is the set of behaviours that must keep working after the change.

## References

- [Raj13] V. Rajlich. *Software Engineering: The Current Practice*, CRC Press, 2013 — Figure 7.9, activity diagram of impact analysis.
