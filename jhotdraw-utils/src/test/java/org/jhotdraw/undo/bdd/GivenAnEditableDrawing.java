/*
 * @(#)GivenAnEditableDrawing.java
 *
 * L6 TestLab2 (BDD): the "Given" stage of the Undo/Redo Given-When-Then
 * scenarios. Sets up the UndoRedoManager (the per-view edit history) and the
 * edits that have been performed on it.
 */
package org.jhotdraw.undo.bdd;

import javax.swing.undo.UndoableEdit;

import org.jhotdraw.undo.UndoRedoManager;

import com.tngtech.jgiven.Stage;
import com.tngtech.jgiven.annotation.Quoted;
import com.tngtech.jgiven.annotation.ScenarioState;
import com.tngtech.jgiven.annotation.ScenarioState.Resolution;

/**
 * Arranges the state of one (or two) drawing views before the user acts.
 *
 * <p>Scenario state is shared with the When/Then stages by <em>field name</em>
 * ({@link Resolution#NAME}) because several fields share the type
 * {@link UndoRedoManager}/{@link UndoableEdit} and could not otherwise be told
 * apart by JGiven's default type-based injection.</p>
 */
public class GivenAnEditableDrawing extends Stage<GivenAnEditableDrawing> {

    @ScenarioState(resolution = Resolution.NAME)
    UndoRedoManager manager;

    @ScenarioState(resolution = Resolution.NAME)
    UndoRedoManager secondManager;

    @ScenarioState(resolution = Resolution.NAME)
    UndoableEdit primaryEdit;

    @ScenarioState(resolution = Resolution.NAME)
    UndoableEdit secondaryEdit;

    @ScenarioState(resolution = Resolution.NAME)
    String editName;

    /** A freshly opened drawing has its own, empty edit history. */
    public GivenAnEditableDrawing an_open_drawing() {
        manager = new UndoRedoManager();
        return self();
    }

    /** A significant editing action (move, resize, attribute change, …) was performed. */
    public GivenAnEditableDrawing a_significant_edit_named_$(@Quoted String name) {
        editName = name;
        primaryEdit = MockEdits.significant(name);
        manager.addEdit(primaryEdit);
        return self();
    }

    /** An insignificant edit (e.g. a pure selection change) was performed. */
    public GivenAnEditableDrawing an_insignificant_edit() {
        primaryEdit = MockEdits.insignificant();
        manager.addEdit(primaryEdit);
        return self();
    }

    /** The edit on top of the undo stack has already been undone (so it is redoable). */
    public GivenAnEditableDrawing the_edit_has_already_been_undone() {
        manager.undo();
        return self();
    }

    /** A second drawing is open in another view, with its own independent edit. */
    public GivenAnEditableDrawing a_second_independent_drawing_with_its_own_edit() {
        secondManager = new UndoRedoManager();
        secondaryEdit = MockEdits.significant("Resize Figure");
        secondManager.addEdit(secondaryEdit);
        return self();
    }
}
