# AnalysisLab — Estimated Impact Set (Portfolio Artifact)

**Course:** Software Maintenance
**Student:** Ignas Beinoras (GitHub: IgnasBeinoras)
**Case study:** JHotDraw (working locally — no remote push for this lab)
**Date:** 2026-04-28
**Linked Change Request:** *Undo / Redo of editing operations* (`ChangeReqLab1_UserStory.md`)
**Linked Concept Location:** `CLLab1_ConceptLocation.md` (9-class initial set)

---

## How the impact set was produced

Followed Figure 7.9 (activity diagram of impact analysis) from [Raj13]:

1. Built an interaction diagram from the concept-location trace (`UndoAction → UndoRedoManager$UndoAction → UndoRedoManager.undo() → UndoableEdit.undo()` and `Tool/Action/Handle → AbstractDrawing.fireUndoableEditHappened → UndoRedoManager.addEdit`). Marked every class as **BLANK**.
2. Marked the seed (`UndoRedoManager` — the core domain class) as **CHANGED**.
3. Iterated: while any class is **NEXT**, pick one and reclassify as **UNCHANGED** / **PROPAGATES** / **CHANGED**, then mark its still-BLANK neighbours **NEXT**. Stopped when no NEXT class remained.
4. Static analysis used: import graphs of every class read; `grep` for `fireUndoableEditHappened`, `extends AbstractUndoableEdit`, `UndoRedoManager`, `CompositeEdit`. Dynamic analysis used: the debugger trace recorded in CLLab1.

### Marking rules used

- **CHANGED** — code in this class will be *modified* to deliver the user story (or its tests will).
- **PROPAGATES** — class is *not modified*, but the change in a CHANGED neighbour reaches it: it must be re-tested or its behaviour re-verified because its inputs/outputs travel through the changed code (typical: producers of `UndoableEdit`s, sibling sample `View`s that wire the same manager).
- **UNCHANGED** — neighbour reachable by the import graph but invariant under this change (typical: JDK classes, generic utilities).

### Termination

Iteration stopped after PROPAGATES classes' neighbours were all already classified or were stable JDK / utility classes (UNCHANGED). The closure has 91 project classes plus 8 JDK contract classes.

---

## Final marks (summary)

| Mark | Count | Notes |
|------|------:|-------|
| CHANGED | 9 | The 9 CL classes from Lab 1 (`UndoAction`, `RedoAction`, `AbstractViewAction`, `DrawView`, `UndoRedoManager`, `CompositeEdit`, `Drawing` + `AbstractDrawing`, `TransformEdit`). |
| PROPAGATES | ~80 | Concrete `UndoableEdit` subclasses, tools, handles, draw-actions, sibling sample views — all flow events into / out of the changed manager. |
| UNCHANGED | ~10 | `Application`, `WeakPropertyChangeListener`, `ResourceBundleUtil`, JDK undo classes. |

---

## Table 1 — Packages visited during impact analysis

> "# of classes" counts only the classes traversed (impact set), not the entire package.
> Module column is the Maven module that owns the package.

| # | Package name | Module | # of classes | Comments — what I learned about the package and how it contributes to the Undo/Redo feature |
|---|--------------|--------|-------------:|---------------------------------------------------------------------------------------------|
| 1 | `org.jhotdraw.action.edit` | jhotdraw-actions | 2 | `UndoAction`, `RedoAction` — application-level controllers bound to *Edit → Undo/Redo* menu items. They are thin shims that look up the per-view real action under the keys `"edit.undo"` / `"edit.redo"` in `View.getActionMap()` and delegate. **CHANGED** — any new behaviour (e.g. updated keyboard shortcuts, presentation-name format) lands here. |
| 2 | `org.jhotdraw.action` | jhotdraw-actions | 1 | `AbstractViewAction` — base class supplying the *active-view-aware* lifecycle (`installViewListeners`, `updateView`, `getActiveView`). Its enabled state combines app + view + own enabled. **CHANGED** — if undo's enablement rules need to be tightened/loosened, the change is here. |
| 3 | `org.jhotdraw.api.app` | jhotdraw-api | 2 | `Application`, `View` — interfaces. `View.getActionMap()` is the contract through which the controller actions reach the per-view manager actions. `Application.ACTIVE_VIEW_PROPERTY` drives view-focus tracking. **PROPAGATES** (`View` — concrete views must keep publishing `UndoAction.ID`/`RedoAction.ID`); **UNCHANGED** (`Application`). |
| 4 | `org.jhotdraw.app` | jhotdraw-app | 1 | `AbstractView` — `JPanel`-based base for sample views. Owns the `ActionMap` that the controllers query. **PROPAGATES** — does not need code changes, but is in every concrete view's parent chain so any change in how Undo/Redo wires into the action map ripples through here. |
| 5 | `org.jhotdraw.beans` | jhotdraw-utils | 1 | `WeakPropertyChangeListener` — wraps the application listener so menu actions don't pin views in memory. **UNCHANGED** — generic listener, not undo-specific. |
| 6 | `org.jhotdraw.undo` | jhotdraw-utils | 4 | The **core domain package**. `UndoRedoManager` extends `javax.swing.undo.UndoManager`, owns the linear stack, exposes the inner Swing `UndoAction`/`RedoAction` actually placed in each view's action map, tracks `hasSignificantEdits`, and guards re-entrancy via `undoOrRedoInProgress` (so listener fan-out during undo doesn't push events back onto the stack). `CompositeEdit` aggregates several edits into one user-visible step. **CHANGED** for `UndoRedoManager` and `CompositeEdit`; **PROPAGATES** for `NonUndoableEdit`, `PropertyChangeEdit` (concrete edits flowing through the manager). |
| 7 | `org.jhotdraw.util` | jhotdraw-utils | 1 | `ResourceBundleUtil` — resolves localised labels (`edit.undo.text`, `edit.redo.text`, `edit.undo.acceleratorKey`, …). **UNCHANGED** — class itself doesn't change; only `Labels.properties` keys may be added. |
| 8 | `org.jhotdraw.draw` | jhotdraw-core | 6 | The **drawing model**. `Drawing` (interface) + `AbstractDrawing` (default impl) are the *source* of `UndoableEditEvent`s — every figure mutation flows through `fireUndoableEditHappened`. `AbstractDrawingView` / `DefaultDrawingView` host the model in the UI. `DefaultDrawingViewTransferHandler` fires edits on paste. **CHANGED** for `Drawing` + `AbstractDrawing`; **PROPAGATES** for the views and the transfer handler (they emit edits but don't change shape). |
| 9 | `org.jhotdraw.draw.figure` | jhotdraw-core | 7 | Concrete figure classes whose mutators (`willChange`/`changed`, `transform`, `setBounds`, `basicAdd/Remove`) emit edits via the owning `Drawing`. `AbstractFigure`/`AbstractCompositeFigure` are the bases; concrete: `BezierFigure`, `LineFigure`, `LineConnectionFigure`, `LabeledLineConnectionFigure`, `QuadTreeCompositeFigure`. **PROPAGATES** — each is a producer site for the manager. |
| 10 | `org.jhotdraw.draw.tool` | jhotdraw-core | 8 | Interactive tools that produce edits as the user draws/edits: `CreationTool`, `BezierTool`, `TextCreationTool`, `TextEditingTool`, `TextAreaCreationTool`, `TextAreaEditingTool`, `ConnectionTool`, `DefaultDragTracker`. Each calls `getDrawing().fireUndoableEditHappened(...)` with an anonymous `AbstractUndoableEdit` describing the gesture. **PROPAGATES**. |
| 11 | `org.jhotdraw.draw.handle` | jhotdraw-core | 13 | Direct-manipulation handles (resize, rotate, move, bezier control points, font-size pull-tab, connector endpoints, round-rectangle radius). Each fires an `UndoableEdit` representing the user-direct gesture. **PROPAGATES**. The classes here are the largest single group of producers and explain why the impact set is so wide. |
| 12 | `org.jhotdraw.draw.event` | jhotdraw-core | 7 | Concrete `AbstractUndoableEdit` subclasses + the attribute-edit listener helper. `TransformEdit` (lossless), `TransformRestoreEdit` (lossy), `SetBoundsEdit`, `BezierNodeEdit`, `AttributeChangeEdit`, `CompositeFigureEdit`, `AbstractAttributeEditorHandler`. These *are* the data of the feature — what the manager stores and replays. `TransformEdit` is **CHANGED** (in CL set, representative); the rest are **PROPAGATES** (ride on the existing `UndoableEdit` contract). |
| 13 | `org.jhotdraw.draw.action` | jhotdraw-core | 16 | Drawing-scoped actions that often bundle several edits into a `CompositeEdit`: `AlignAction`, `MoveAction`, `MoveConstrainedAction`, `AttributeAction`, `ApplyAttributesAction`, `DefaultAttributeAction`, `DrawingAttributeAction`, `GroupAction`, `BringToFrontAction`, `SendToBackAction`, `AttributeToggler`, `AbstractDrawingEditorAction`, `AbstractDrawingViewAction`, `AbstractSelectedAction`, `SelectionColorChooserHandler`, `DrawingColorChooserHandler`. **PROPAGATES** — they are *callers* of `CompositeEdit` and `fireUndoableEditHappened`. |
| 14 | `org.jhotdraw.gui.action` | jhotdraw-gui | 2 | `FontChooserHandler`, `EditCanvasPanel` — UI controls that emit edits when the user changes font / canvas attributes. **PROPAGATES**. |
| 15 | `org.jhotdraw.samples.draw` | jhotdraw-samples-misc | 2 | `DrawView` (CL set, **CHANGED**), `DrawingPanel` — wire each view's `UndoRedoManager` into its `ActionMap` under `UndoAction.ID`/`RedoAction.ID` and forward `hasSignificantEdits` to `setHasUnsavedChanges`. The **per-view** scoping of undo lives here. |
| 16 | `org.jhotdraw.samples.svg`, `.svg.figures`, `.svg.action`, `.svg.gui` | jhotdraw-samples-misc | 9 | Sibling SVG sample. `SVGView`, `SVGDrawingPanel`, `ActionsToolBar`, `SVGPathFigure`, `SVGImageFigure`, `SVGAttributedFigure`, `SVGBezierFigure`, `SVGRectRadiusHandle`, `CombineAction`. Same wiring shape as the Draw sample. **PROPAGATES** — every contract change in the manager forces this sample to be re-verified. |
| 17 | `org.jhotdraw.samples.odg`, `.odg.figures` | jhotdraw-samples-misc | 6 | ODG (OpenDocument graphics) sibling sample. `ODGView`, `ODGDrawingPanel`, `ODGPathFigure`, `ODGAttributedFigure`, `ODGBezierFigure`, `ODGRectRadiusHandle`. **PROPAGATES**. |
| 18 | `org.jhotdraw.samples.net` | jhotdraw-samples-misc | 2 | `NetView`, `NetPanel` — small networking-diagram sample. Uses `UndoRedoManager`. **PROPAGATES**. |
| 19 | `org.jhotdraw.samples.pert` | jhotdraw-samples-misc | 2 | `PertView`, `PertPanel` — PERT-chart sample. **PROPAGATES**. |
| 20 | `org.jhotdraw.samples.teddy` | jhotdraw-samples-misc | 2 | `TeddyView`, `FindDialog` — text-editor sample. `FindDialog` is the only place outside the draw stack that uses `CompositeEdit` (find/replace bundling). **PROPAGATES** — confirms that `CompositeEdit`'s contract has reach beyond the draw module. |
| 21 | `javax.swing.undo` (JDK boundary) | — | 8 | `UndoManager` (parent of `UndoRedoManager`), `UndoableEdit`, `AbstractUndoableEdit`, `CompoundEdit` (parent of `CompositeEdit`), `UndoableEditListener`, `UndoableEditEvent`, `CannotUndoException`, `CannotRedoException`. **UNCHANGED** — JDK contract, listed as the boundary of the impact closure. |

**Total project classes visited: ≈ 91.** Plus 8 JDK boundary classes (UNCHANGED).

---

## What I learned about the Undo/Redo feature from the impact walk

- **The blast radius is wide because the feature is event-driven.** The "central" code (rows 1–6 above, ~14 classes) is small, but every figure / tool / handle / draw-action that mutates the model is a *producer* into that central code, so any change to the `UndoableEdit` or manager contract automatically propagates to ~80 classes.
- **The per-view boundary is the manager + the action map.** Each sample's `*View` is responsible for installing its own `UndoRedoManager` as an `UndoableEditListener` on its `Drawing` and publishing the manager's two inner Swing actions under `UndoAction.ID` / `RedoAction.ID`. Five sample views replicate this pattern (Draw, SVG, ODG, Net, Pert; Teddy uses the same manager but for a text document).
- **Significance is the subtle invariant.** `UndoRedoManager.hasSignificantEdits` and `CompositeEdit.isSignificant()` decide whether a view becomes "dirty". Concrete edits' `isSignificant()` flags are spread across `org.jhotdraw.draw.event.*` and the anonymous edits in tools/handles. A change to the manager's "what counts as significant" rule needs cross-package re-verification.
- **`CompositeEdit` has reach outside Draw.** `FindDialog` in the Teddy sample uses `CompositeEdit` to bundle find-and-replace into one undoable step. So `CompositeEdit` changes affect text scenarios too, not just figure scenarios.
- **The JDK boundary is well-isolated.** Concrete edits extend `AbstractUndoableEdit`; the manager extends `UndoManager`. We can change the JHotDraw side (manager behaviour, `CompositeEdit`, the controller actions) without touching the JDK contract — which is good news for the change request.

---

## References

- [Raj13] M. Rajlich. *Software Engineering: The Current Practice* — Figure 7.9, activity diagram of impact analysis (the algorithm executed above).
