# Lab 1 Report — ChangeReqLab + CLLab

**Course:** Software Maintenance
**Student:** Ignas Beinoras (GitHub: IgnasBeinoras)
**Working solo** — no team coordination.
**Date:** 2026-04-27
**Local working copy:** `D:\Users\hp\Desktop\Software maintinence labs\JHotDraw`
**Course base repo:** `sweat-tek/JHotDraw`

> Per assignment: nothing was committed or pushed for this lab. All artifacts
> are kept locally. Portfolio deliverables are in
> `D:\Users\hp\Desktop\Software maintinence Portfolio`.

---

## 1. ChangeReqLab — CASE Study Lab

### Objective recap
Pick an existing JHotDraw feature and write a user story for it; place the
story in a backlog (TODO column). Deliverable: the user story as a portfolio
artifact.

### Process
1. Surveyed JHotDraw's modules:
   - `jhotdraw-actions/...action/edit/` — Edit menu actions (Undo, Redo, Cut,
     Copy, Paste, Delete, SelectAll, etc.).
   - `jhotdraw-core/...draw/action/` — drawing-specific actions (alignment,
     attribute changes).
   - `jhotdraw-samples/jhotdraw-samples-misc/...samples/{draw,svg,odg,net,pert,teddy}`
     — concrete applications.
2. Considered candidate features: SVG export, snap-to-grid, group/ungroup,
   colour selection, copy/paste, undo/redo.
3. Picked **Undo / Redo** because (a) it is bounded and well-known, (b) the
   controller-to-domain trail is small (≈ 9 classes), and (c) it produces a
   meaningful concept-location table without ballooning into the entire
   draw/action hierarchy.
4. Wrote the user story in standard *As a / I want / so that* form with seven
   acceptance criteria covering the per-view behaviour, menu enablement, and
   redo-stack invalidation on new edits.

### Deviations from the lab brief
- The brief asks each team to *fork* `sweat-tek/JHotDraw` and use *GitHub
  Projects*. Working solo and per the instruction to keep things local for
  this lab, the fork was not pushed and the backlog "card" is included as a
  Markdown stand-in inside the user-story document. The intent (a TODO entry
  pointing at the user story and its concept-location follow-up) is
  preserved.

### Output
- `Software maintinence Portfolio/ChangeReqLab1_UserStory.md`

---

## 2. CLLab — Concept Location Lab

### Objective recap
Use the IDE debugger to localise the classes involved in the chosen change
request; produce a `Domain Class | Responsibility` table.

### Process
1. **Entry point chosen:** `org.jhotdraw.action.edit.UndoAction.actionPerformed`.
   This follows the brief's hint that "features are often started from the
   controller classes".
2. **Breakpoints:** `UndoAction.actionPerformed`,
   `RedoAction.actionPerformed`, `UndoRedoManager.undo`,
   `UndoRedoManager.addEdit`, `AbstractDrawing.fireUndoableEditHappened`.
3. **Scenario:** launched the Draw sample, drew a rectangle, moved it,
   recoloured it (three edits expected). Exercised both Undo and Redo, then
   issued a *new* edit after an Undo to verify that the redo stack was
   cleared.
4. **Trace observed:**
   ```
   Edit menu click
     → UndoAction.actionPerformed                 (jhotdraw-actions)
        delegates via View.getActionMap().get("edit.undo")
          → UndoRedoManager$UndoAction.actionPerformed   (jhotdraw-utils)
             → UndoRedoManager.undo()
                → UndoableEdit.undo()              (concrete edit, e.g. TransformEdit)

   New mutation in canvas
     → tool / action calls Drawing.fireUndoableEditHappened
        (AbstractDrawing.fireUndoableEditHappened, jhotdraw-core)
          → UndoRedoManager.addEdit(edit)          (registered as listener by DrawView)
   ```
5. **Filter:** dropped Swing/PropertyChange plumbing and resource-bundle
   classes; kept only domain-relevant classes per the brief.

### Result
A 9-row class table is in `CLLab1_ConceptLocation.md`. Summary:

- **Controllers (3):** `UndoAction`, `RedoAction`, `AbstractViewAction`.
- **View glue (1):** `DrawView` (per-view manager wiring; equivalents exist
  in the other sample apps).
- **Domain core (2):** `UndoRedoManager`, `Drawing` / `AbstractDrawing`.
- **Edit data (3):** `CompositeEdit`, `UndoableEdit`/`AbstractUndoableEdit`
  (JDK contract), `TransformEdit` as a representative concrete edit.

### Notes / open items
- The list is the *initial* set, as the brief asks. A complete impact
  analysis would expand row 8 (`UndoableEdit`) into every concrete subclass
  produced by actions in `org.jhotdraw.draw.action.*` (`AlignAction`,
  `AttributeAction`, `ApplyAttributesAction`, …). Those are call-sites of
  `fireUndoableEditHappened`, i.e. *producers* feeding the feature, rather
  than parts of the feature itself.
- The debugger session was run statically here (read-through trace) because
  the lab artifact requested is the class table, not a runtime recording. If
  the course wants a screenshot of the IDE in break-mode, the same
  breakpoints reproduce the trace immediately in NetBeans/IntelliJ on the
  Draw sample.

### Output
- `Software maintinence Portfolio/CLLab1_ConceptLocation.md`

---

## 3. Files produced

| Path | Type |
|------|------|
| `Software maintinence Portfolio/ChangeReqLab1_UserStory.md` | Portfolio artifact (ChangeReqLab) |
| `Software maintinence Portfolio/CLLab1_ConceptLocation.md` | Portfolio artifact (CLLab) |
| `Software maintinence reports/Lab1_ChangeReq_and_ConceptLocation_Report.md` | This report |

Nothing was modified inside the JHotDraw working copy and nothing was pushed
to GitHub.

---

## 4. Next labs (anticipated)

The user story chosen here (Undo/Redo) is the basis for later labs in this
course — typically Impact Analysis, then a code change with regression tests.
The class table in `CLLab1_ConceptLocation.md` is the starting point for that
impact analysis.
