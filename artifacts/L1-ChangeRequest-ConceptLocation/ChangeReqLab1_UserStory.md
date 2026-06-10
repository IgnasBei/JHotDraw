# ChangeReqLab — User Story (Portfolio Artifact)

**Course:** Software Maintenance
**Student:** Ignas Beinoras (GitHub: IgnasBeinoras)
**Case study:** JHotDraw (fork of `sweat-tek/JHotDraw`, working locally — no remote push for this lab)
**Date:** 2026-04-27

---

## Selected Existing Feature

**Undo / Redo of editing operations** in the JHotDraw drawing applications (Draw, SVG, ODG, Net, Pert, Teddy samples). The feature is bound to the standard *Edit → Undo* and *Edit → Redo* menu items and is per-view (each open drawing has its own edit history).

Why this feature was picked:

- It is a clearly bounded, well-known feature with an obvious user-facing behaviour.
- The controller entry point (`UndoAction` / `RedoAction`) is easy to localise, and the trail to the domain (`UndoRedoManager`, `Drawing`, `UndoableEdit`) is small enough to fit a single user story.
- It has a non-trivial domain side (event listening, edit stack, significant-edit tracking) so the concept-location lab in CLLab1 produces a meaningful class table rather than just one or two files.

---

## User Story

> **As a** user editing a drawing in JHotDraw,
> **I want to** undo my last editing action and, if I change my mind, redo it,
> **so that** I can experiment freely and recover from mistakes without losing earlier work.

### Conversation / Notes

- "Editing action" means anything that mutates the drawing model: creating a figure, moving / resizing / rotating a figure, changing an attribute (colour, stroke, font), grouping, deleting, pasting, etc.
- The feature is per-view: undoing in one open drawing must not affect another open drawing.
- The Edit menu items must reflect the current state — disabled when there is nothing to undo/redo, and their labels should show the name of the next edit on the stack (e.g. "Undo Move").

### Acceptance Criteria

| # | Given | When | Then |
|---|-------|------|------|
| 1 | A drawing is open and at least one edit has been made | The user invokes *Edit → Undo* (or `Ctrl+Z`) | The most recent edit is reversed and the canvas is repainted |
| 2 | An edit has just been undone | The user invokes *Edit → Redo* (or `Ctrl+Shift+Z` / `Ctrl+Y`) | The undone edit is re-applied |
| 3 | The drawing has just been opened or all edits have been discarded | The user opens the Edit menu | *Undo* and *Redo* items are both disabled |
| 4 | An edit is on the undo stack | The user opens the Edit menu | The *Undo* item shows a presentation name describing the edit ("Undo Move", "Undo Change Attribute", …) |
| 5 | Two drawings are open in two views | The user undoes in view A | View B's edit history is unchanged; view B's *Undo*/*Redo* state is independent |
| 6 | A significant edit is on the undo stack | The user looks at the window title / save-state | The view is marked as having unsaved changes (`hasSignificantEdits == true`) |
| 7 | A new edit is performed after a sequence of undos | The redo stack is checked | The redo stack is cleared (standard editor semantics) |

### Out of Scope

- Persisting the undo history across application restarts.
- A visual "history palette" (undo tree). Only the linear undo/redo stack provided by `javax.swing.undo.UndoManager` is in scope.
- Selective undo (undoing an edit that is not on the top of the stack).

### Definition of Done

- All seven acceptance criteria pass by manual exercise of the Draw sample app.
- No regressions in the existing JHotDraw unit tests.
- Concept-location artifact (CLLab1) lists the domain classes touched by the feature.

---

## Backlog Card (TODO column — local Markdown stand-in for GitHub Projects)

```
[TODO] US-001 — Undo/Redo of editing operations
       owner: IgnasBeinoras
       size:  M
       links: ChangeReqLab1_UserStory.md, CLLab1_ConceptLocation.md
       notes: kept local for Lab 1 per course instructions; not pushed to fork
```

> The course materials reference GitHub Projects for backlog tracking. Per the
> assignment instructions for this lab, the card is kept locally in this
> document instead of pushed to a remote board.
