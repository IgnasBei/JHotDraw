# ActualizationLab — SOLID & Clean Architecture in the JHotDraw Undo/Redo Case Study (Portfolio Artifact)

**Course:** Software Maintenance
**Student:** Ignas Beinoras (GitHub: IgnasBeinoras)
**Case study:** JHotDraw — feature spine: *Undo / Redo of editing operations*
**Date:** 2026-04-30
**Linked artifacts:** `ChangeReqLab1_UserStory.md`, `CLLab1_ConceptLocation.md`, `AnalysisLab1_ImpactSet.md`, `RefactoringLab1_Portfolio.md`
**Local-only:** all references are to the local working copy at `Software maintinence labs/JHotDraw`. No commits, no push.

---

## 1. Why this lab is built on the Undo/Redo spine

The Actualization phase is described in the lab brief as
*"the implementation of new functionality, its incorporation into the old
code, and change propagation that seeks out and updates all places in the
old code that require secondary modification"* — i.e. the doing-side of
the maintenance cycle, after change request → concept location → impact
analysis → refactoring (Labs 1, 1b, 2, 3 in this portfolio).

So the most useful thing this lab can produce is **a SOLID / Clean
Architecture audit of the very code I would now actualize against**.
That code is the 9-class CHANGED ring from `AnalysisLab1_ImpactSet.md`
plus the supporting classes seen in `CLLab1_ConceptLocation.md`.

---

## 2. SOLID principles in the Undo/Redo case study

Each subsection cites concrete classes from the local JHotDraw working
copy (paths relative to `jhotdraw-*` modules).

### 2.1 SRP — Single Responsibility Principle

> *"A class should have one, and only one, reason to change."* — Martin

| Class | SRP fit | Why |
|-------|---------|-----|
| `org.jhotdraw.undo.UndoRedoManager` | **Good** | Only reason to change: the undo-stack policy (what is significant, when to discard, how to label menu items). It does *not* know about specific figures, drawings, or edit content. |
| `org.jhotdraw.undo.CompositeEdit` | **Good** | One reason to change: how multiple `UndoableEdit`s should be aggregated as one user-visible step. |
| `org.jhotdraw.action.edit.UndoAction` / `RedoAction` (after L3 refactoring) | **Good** | Each is now ~41 lines, does one thing: declare the action id under which the per-view real action is published. The wiring lives in `AbstractUndoRedoViewAction`. **Before** the L3 refactoring these classes mixed *id-declaration* and *Swing-wiring* — two reasons to change. |
| `org.jhotdraw.draw.AbstractDrawing` | **Mild violation** | Holds (a) the drawing model state, (b) the listener registry for `UndoableEditListener`, and (c) the edit-firing protocol via `fireUndoableEditHappened`. Three reasons to change. The reason it's tolerable: Java's listener idiom couples the source to its event protocol, which is a well-known Swing-era constraint. |
| `org.jhotdraw.samples.draw.DrawView` (and its 4 siblings: SVG/ODG/Net/Pert) | **Violation** | A `*View` mixes (a) view geometry & rendering, (b) per-view `UndoRedoManager` ownership and `ActionMap` wiring, (c) `hasSignificantEdits` → `setHasUnsavedChanges` propagation. Three+ reasons to change *and* the same wiring is repeated five times — see §5 below for the "next refactoring". |

### 2.2 OCP — Open/Closed Principle

> *"Software entities should be open for extension, but closed for modification."*

| Where it works | Why |
|----------------|-----|
| `UndoableEdit` interface (JDK) — every edit (`TransformEdit`, `AttributeChangeEdit`, anonymous edits inside tools, …) plugs into `UndoRedoManager.addEdit(UndoableEdit)` *without* the manager being modified | Classic OCP via polymorphism; adding `BezierNodeEdit` did not require re-opening `UndoRedoManager`. |
| `AbstractUndoRedoViewAction` (introduced in L3) | New menu actions sharing the "real per-view delegate" pattern can extend the base instead of editing it — e.g. a future `RevertAction` could reuse the same Template Method skeleton. |

| Where it falls short | Why |
|----------------------|-----|
| `UndoRedoManager.updateActions()` (after L3 refactoring it now uses `configureMenuAction`, but it still hard-codes exactly two actions: `undoAction`, `redoAction`) | If a third managed action ever needed updating in lockstep (e.g. an "Undo All" action), `updateActions()` would have to be modified. A more OCP-clean shape would be a list of `(action, canPerformSupplier, presentationSupplier, fallbackKey)` tuples iterated over. Marked as future work; out of scope for this lab. |
| `CompositeEdit.addEdit` carries `if (anEdit == this) end()` and `if (anEdit instanceof CompositeEdit) ...` — instanceof is the canonical OCP smell | Fine for the JHotDraw use case, but a future "selective undo" extension would push this conditional past its breaking point and need *Replace Conditional with Polymorphism*. |

### 2.3 LSP — Liskov Substitution Principle

> *"Subtypes must be substitutable for their base types."*

- `UndoRedoManager extends javax.swing.undo.UndoManager` — **substitutable**: anywhere a JDK `UndoManager` is expected, the JHotDraw subclass can replace it. The added `undoOrRedoInProgress` guard *narrows* what the subtype does on input (it ignores `addEdit` calls during an undo) but does so for inputs the parent's contract doesn't promise behaviour for (re-entrant adds during undo) — so this is LSP-safe by the "doesn't break what the parent promised" reading.
- `CompositeEdit extends javax.swing.undo.CompoundEdit` — **subtle LSP twist**. `addEdit(this)` triggers `end()`, a special protocol the parent class doesn't advertise. Code that has only a `CompoundEdit` reference and calls `addEdit(theSameInstance)` would behave differently. JHotDraw is the only client and it knows the protocol, so this is *acceptable specialization* — but it's the place an LSP audit would flag.
- Concrete edits (`TransformEdit`, `AttributeChangeEdit`, `BezierNodeEdit`, `SetBoundsEdit`, `TransformRestoreEdit` …) **all** extend `AbstractUndoableEdit` and respect the contract (`undo`/`redo`/`canUndo`/`canRedo`/`isSignificant`/`getPresentationName`). They are pure, well-behaved subtypes — this is what makes the manager's polymorphism viable.

### 2.4 ISP — Interface Segregation Principle

> *"Clients should not be forced to depend on methods they do not use."*

| Interface | ISP score | Notes |
|-----------|-----------|-------|
| `javax.swing.undo.UndoableEdit` | **Wide** (10 methods: `undo`, `redo`, `canUndo`, `canRedo`, `die`, `addEdit`, `replaceEdit`, `isSignificant`, `getPresentationName`, `getUndoPresentationName`, `getRedoPresentationName`) | A JHotDraw concrete edit only really cares about `undo`, `redo`, `isSignificant`, `getPresentationName`. The rest is paid for via `AbstractUndoableEdit`'s defaults. Not *clean*, but the cost is paid by the JDK once and inherited free. |
| `org.jhotdraw.api.app.View` | **Focused for our feature** | The undo/redo controllers only need `getActionMap()` and the `ACTIVE_VIEW_PROPERTY` change channel. They do not depend on layout, file-handling, or focus methods of `View` — even though `View` advertises them. |
| `javax.swing.event.UndoableEditListener` | **Tight** (one method) | Textbook ISP — one reason it's the right abstraction between `Drawing` and `UndoRedoManager`. |

### 2.5 DIP — Dependency Inversion Principle

> *"Depend on abstractions, not concretions."*

Strong points:

- `UndoRedoManager` depends on `UndoableEdit` (abstract) — concrete edits are *plugins*. Adding a new figure with a new edit class requires no change to the manager.
- `AbstractDrawing` exposes `addUndoableEditListener(UndoableEditListener)` — the manager subscribes through an interface. This is the dependency-inverting wire between the model and the use case.

Weak points:

- `UndoRedoManager` directly creates Swing `AbstractAction` instances and exposes them through `getUndoAction()`/`getRedoAction()`. This couples the use case ("undo the last edit") to the UI framework ("Swing Action"). A clean-architecture purist would split this into:
  - a framework-free `UndoUseCase` exposing `boolean canUndo()`, `void undo()`, `String label()`, plus an `Observable<state>` channel; and
  - a Swing-side `UndoActionAdapter` that subscribes to the use case and pushes into Swing.
  This is not a defect — it's the trade-off Swing-era applications routinely make — but it's where the dependency arrow points outward when it shouldn't.
- `UndoRedoManager.getLabels()` reaches statically into `ResourceBundleUtil`. That's a hidden dependency on a concrete utility (`Service Locator` flavour). Testability would improve with constructor-injected `ResourceBundleUtil`.

---

## 3. Clean Architecture mapped onto the case study

Robert C. Martin's concentric circles, applied to JHotDraw with the
Undo/Redo feature in focus:

```
+----------------------------------------------------------------+
|  Frameworks & Drivers (outermost)                              |
|    javax.swing.JFrame / JPanel / AbstractAction                |
|    javax.swing.undo.UndoManager / CompoundEdit                 |
|    java.util.ResourceBundle, java.beans.PropertyChangeSupport  |
|                                                                |
|  +----------------------------------------------------------+  |
|  |  Interface Adapters                                      |  |
|  |    UndoAction / RedoAction (jhotdraw-actions)            |  |
|  |    AbstractUndoRedoViewAction (introduced in L3)         |  |
|  |    DrawView / SVGView / ODGView / NetView / PertView     |  |
|  |    UndoRedoManager$UndoAction (inner Swing adapter)      |  |
|  |    UndoRedoManager$RedoAction (inner Swing adapter)      |  |
|  |                                                          |  |
|  |  +----------------------------------------------------+  |  |
|  |  |  Use Cases                                         |  |  |
|  |  |    UndoRedoManager — "undo last edit",             |  |  |
|  |  |                       "redo last undone",          |  |  |
|  |  |                       "track significance"         |  |  |
|  |  |    CompositeEdit  — "bundle gestures into one      |  |  |
|  |  |                       undoable step"               |  |  |
|  |  |                                                    |  |  |
|  |  |  +----------------------------------------------+  |  |  |
|  |  |  |  Entities (innermost)                        |  |  |  |
|  |  |  |    UndoableEdit (contract for one reversible |  |  |  |
|  |  |  |                   model mutation)            |  |  |  |
|  |  |  |    Drawing / AbstractDrawing (model)         |  |  |  |
|  |  |  |    Figure subtree (BezierFigure, …)          |  |  |  |
|  |  |  |    TransformEdit / AttributeChangeEdit /     |  |  |  |
|  |  |  |    SetBoundsEdit / BezierNodeEdit (concrete  |  |  |  |
|  |  |  |    business edits — they ARE the domain)     |  |  |  |
|  |  |  +----------------------------------------------+  |  |  |
|  |  +----------------------------------------------------+  |  |
|  +----------------------------------------------------------+  |
+----------------------------------------------------------------+
```

### 3.1 Where dependencies point

The Clean Architecture rule is: **source-code dependencies point inward only**.

Honoured:
- Concrete edits (`TransformEdit`, `AttributeChangeEdit`, …) sit in
  Entities and depend on no inner ring — they implement
  `UndoableEdit` and reference only `Figure`/`Drawing`/`java.awt.geom`.
- `UndoRedoManager` (use case) depends *outward only* via the
  abstraction `UndoableEdit` — and inward by extending the JDK
  framework class. That second arrow is the tension below.
- `UndoAction`/`RedoAction` (interface adapter) depend on
  `UndoRedoManager` only indirectly — through `View.getActionMap()`
  keys (`"edit.undo"`, `"edit.redo"`). They never `import` the
  manager. This is a clean adapter boundary: a string key is the seam.

Violated:
- `UndoRedoManager extends javax.swing.undo.UndoManager` — a
  use-case-layer class extends a framework-layer class. Source-code
  dependency points **outward**.
- `UndoRedoManager` constructs and exposes Swing `Action` objects
  (`getUndoAction()`/`getRedoAction()`) — use case knows the UI
  framework's vocabulary.
- `CompositeEdit extends CompoundEdit` — same story for the second
  use case class.

These are JHotDraw's *legacy* compromises with Swing. They were
reasonable in 2005; they would be the first targets if a "modernise to
Clean Architecture" change request landed.

### 3.2 The seams that already exist

JHotDraw isn't a clean-architecture textbook example, but the
*right seams* are present and used:

1. **`UndoableEdit` interface** — the only thing the manager depends on
   from the entities side. Adding a new figure type and its edits does
   not propagate into `UndoRedoManager`.
2. **`UndoableEditListener` interface** — the only thing
   `AbstractDrawing` knows about its observer (the manager). The model
   does not import the manager.
3. **`View.getActionMap()` string keys (`"edit.undo"` / `"edit.redo"`)**
   — the seam between the application-level controllers in
   `jhotdraw-actions` and the per-view manager in
   `jhotdraw-utils`/`jhotdraw-samples`. A string key is a poor man's
   abstract interface, but it *does* keep the controllers
   compile-independent of the manager.

Each of these seams is what made the impact set in
`AnalysisLab1_ImpactSet.md` knowable: PROPAGATES classes propagate
*through* these seams without a code-edit propagation.

---

## 4. Clean Code principles I noticed during Actualization

The Clean Code-style cleanups already landed in L3 (Refactoring Lab); I
reuse them here as worked examples:

| Principle (Martin, *Clean Code*) | Where it shows up |
|----------------------------------|-------------------|
| *Functions should do one thing* | `UndoRedoManager.runGuarded(Runnable)` (L3) — it owns the `undoOrRedoInProgress` invariant; `undo()/redo()/undoOrRedo()` each became a single-line "intention" call. |
| *Don't repeat yourself* | `AbstractUndoRedoViewAction` (L3) — collapsed two 115-line near-clones into one 96-line base + two 41-line subclasses. |
| *Meaningful names* | `runGuarded` over `withFlag`; `configureMenuAction` over `set`; `realActionListener` over `redoActionPropertyListener` (which was *misnamed* in the original `UndoAction.java` — copy-paste residue). |
| *Comments explain why, not what* | The new helpers carry one-line Javadoc explaining the *invariant* (`runGuarded`: "so listener fan-out during the operation does not push events back onto the stack"), not what the code does line-by-line. |
| *Error handling: don't let it eat the logic* | `runGuarded` keeps the `finally` block tiny and clear; `CannotUndoException`/`CannotRedoException` propagate transparently because they're `RuntimeException`s — no try/catch noise added. |

---

## 5. What this audit suggests for the next change request

Following the lab brief's framing — Actualization plus *change
propagation that seeks out and updates all places in the old code that
require secondary modification* — the audit points at one
high-payoff next step inside the existing impact set:

> **Extract `UndoRedoSupport`** from the per-view wiring repeated in
> `DrawView`, `SVGView`, `ODGView`, `NetView`, `PertView`, and
> `TeddyView`. Each `*View` constructs an `UndoRedoManager`, registers
> it as an `UndoableEditListener` on its document/drawing, publishes
> the manager's two actions into its `ActionMap`, and forwards
> `hasSignificantEdits` to `setHasUnsavedChanges`. That's six copies
> of the same routine — exactly the SRP violation pointed out in
> §2.1, and exactly the place change propagation is currently most
> expensive.

This is the *next* lab, not this one — this lab's deliverable is the
audit itself.

---

## 6. References

- Martin, R. C. *Clean Code: A Handbook of Agile Software Craftsmanship*. Prentice Hall, 2008.
- Martin, R. C. *Clean Architecture: A Craftsman's Guide to Software Structure and Design*. Prentice Hall, 2017.
- Martin, R. C. *Agile Software Development: Principles, Patterns, and Practices* (SOLID origin). Prentice Hall, 2002.
- [Ker05] Kerievsky, J. *Refactoring to Patterns* — used in L3 and re-cited where Clean Code principles overlap with the refactorings already applied.
- Local linked artifacts: `ChangeReqLab1_UserStory.md`, `CLLab1_ConceptLocation.md`, `AnalysisLab1_ImpactSet.md`, `RefactoringLab1_Portfolio.md`.
