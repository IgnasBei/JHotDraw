# Lab 4 — Actualization Lab (Report)

**Course:** Software Maintenance
**Student:** Ignas Beinoras
**Date:** 2026-04-30
**Lab brief:** `L4ActualizationLab.pdf`
**Case study:** JHotDraw (`Software maintinence labs/JHotDraw/`, local working copy)
**Linked feature:** *Undo / Redo of editing operations*
**Portfolio artifact:** `Software maintinence Portfolio/ActualizationLab1_Portfolio.md`

---

## 1. Objective

Per the lab brief:
- Understand and explain Clean Architecture in context of Actualization.
- Understand and explain Clean Code principles in context of Actualization.

Portfolio work:
- Provide examples of the SOLID principles in context of the case study.
- Explain Clean Architecture in context of the case study.

## 2. Approach

This lab has no explicit coding step; it is a discursive / portfolio
lab building on the same JHotDraw Undo/Redo feature spine that Labs
1, 2, and 3 of this portfolio analyzed and refactored. I therefore
audited the CHANGED-ring classes from `AnalysisLab1_ImpactSet.md`
(plus the newly-introduced `AbstractUndoRedoViewAction` from L3)
against (a) the five SOLID principles and (b) Clean Architecture's
concentric-circles model.

No JHotDraw source files were changed in this lab. All artifacts are
markdown writeups; no commits, no push.

## 3. Outputs

- `Software maintinence Portfolio/ActualizationLab1_Portfolio.md` — the
  full audit (SOLID class-by-class, Clean Architecture circle mapping,
  seams, dependency-rule violations, suggested next step).

## 4. Key findings (one-line summary each)

- **SRP** — best on `UndoRedoManager`/`CompositeEdit` and on the L3-slimmed `UndoAction`/`RedoAction`; worst on the sample `*View` classes (six copies of the same wiring).
- **OCP** — strongest at the `UndoableEdit` polymorphism boundary; weakest where `UndoRedoManager.updateActions()` hard-codes the two action references and where `CompositeEdit.addEdit` uses `instanceof`.
- **LSP** — clean for concrete edits; subtle for `CompositeEdit extends CompoundEdit` (the `addEdit(this) ⇒ end()` protocol is a specialization the parent doesn't advertise).
- **ISP** — fine in JHotDraw's own interfaces; the wide JDK `UndoableEdit` interface is paid for via `AbstractUndoableEdit`.
- **DIP** — strong at the model↔manager seam (`UndoableEditListener`); broken where the use case (`UndoRedoManager`) extends framework code (`javax.swing.undo.UndoManager`) and creates Swing `Action`s itself.
- **Clean Architecture** — domain entities (`UndoableEdit`, `Drawing`, concrete edits) are framework-free; use cases (`UndoRedoManager`, `CompositeEdit`) inherit from Swing — that is the dependency-rule violation that defines JHotDraw's age.
- **Clean Code** — the L3 refactorings (`runGuarded`, `configureMenuAction`, `AbstractUndoRedoViewAction`) are concrete worked examples of *Functions do one thing*, *DRY*, *Meaningful Names*, and *Comments explain why*.

## 5. Suggested next-lab refactoring (out of scope here)

Extract a `UndoRedoSupport` helper from the six per-view wirings in
`DrawView` / `SVGView` / `ODGView` / `NetView` / `PertView` /
`TeddyView`. This is the SRP/DRY violation with the largest blast
radius left in the impact set.

## 6. Conclusion

The Actualization-phase audit cleanly aligned with the change-request
spine traced through Labs 1–3: the same nine CHANGED-ring classes
that carry the feature also carry both its strongest design choices
(`UndoableEdit` polymorphism, `UndoableEditListener` seam) and its
clearest legacy compromises (use cases extending Swing framework
classes; per-view wiring duplicated five times). That is exactly the
kind of mixed picture Clean Architecture / Clean Code are meant to
make explicit.
