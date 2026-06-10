/*
 * @(#)WhenTheUser.java
 *
 * L6 TestLab2 (BDD): the "When" stage of the Undo/Redo scenarios. Models the
 * user invoking Edit -> Undo / Edit -> Redo, or performing a further edit.
 */
package org.jhotdraw.undo.bdd;

import javax.swing.undo.UndoableEdit;

import org.jhotdraw.undo.UndoRedoManager;

import com.tngtech.jgiven.Stage;
import com.tngtech.jgiven.annotation.Quoted;
import com.tngtech.jgiven.annotation.ScenarioState;
import com.tngtech.jgiven.annotation.ScenarioState.Resolution;

/** The actions a user takes through the Edit menu. */
public class WhenTheUser extends Stage<WhenTheUser> {

    @ScenarioState(resolution = Resolution.NAME)
    UndoRedoManager manager;

    @ScenarioState(resolution = Resolution.NAME)
    UndoableEdit newEdit;

    /** Edit -> Undo (Ctrl+Z). */
    public WhenTheUser invokes_undo() {
        manager.undo();
        return self();
    }

    /** Edit -> Redo (Ctrl+Y / Ctrl+Shift+Z). */
    public WhenTheUser invokes_redo() {
        manager.redo();
        return self();
    }

    /** Performs a brand-new significant edit after a sequence of undos. */
    public WhenTheUser performs_a_new_$_edit(@Quoted String name) {
        newEdit = MockEdits.significant(name);
        manager.addEdit(newEdit);
        return self();
    }
}
