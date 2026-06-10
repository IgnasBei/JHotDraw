/*
 * @(#)ThenTheDrawing.java
 *
 * L6 TestLab2 (BDD): the "Then" stage of the Undo/Redo scenarios. Domain
 * assertions use the AssertJ library (per the lab brief); collaborator calls
 * are verified with Mockito.
 */
package org.jhotdraw.undo.bdd;

import javax.swing.Action;
import javax.swing.undo.UndoableEdit;

import org.jhotdraw.undo.UndoRedoManager;

import com.tngtech.jgiven.Stage;
import com.tngtech.jgiven.annotation.Quoted;
import com.tngtech.jgiven.annotation.ScenarioState;
import com.tngtech.jgiven.annotation.ScenarioState.Resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Verifies the resulting state of the drawing view(s) after the user acts. */
public class ThenTheDrawing extends Stage<ThenTheDrawing> {

    @ScenarioState(resolution = Resolution.NAME)
    UndoRedoManager manager;

    @ScenarioState(resolution = Resolution.NAME)
    UndoRedoManager secondManager;

    @ScenarioState(resolution = Resolution.NAME)
    UndoableEdit primaryEdit;

    @ScenarioState(resolution = Resolution.NAME)
    UndoableEdit secondaryEdit;

    /** AC1: the last edit was reversed and is now available to redo. */
    public ThenTheDrawing the_most_recent_edit_is_reversed() {
        verify(primaryEdit, times(1)).undo();
        assertThat(manager.canUndo()).as("nothing left to undo").isFalse();
        assertThat(manager.canRedo()).as("undone edit is now redoable").isTrue();
        return self();
    }

    /** AC2: the previously undone edit was replayed. */
    public ThenTheDrawing the_edit_is_re_applied() {
        verify(primaryEdit, times(1)).redo();
        assertThat(manager.canUndo()).as("redone edit can be undone again").isTrue();
        return self();
    }

    /** AC3: both Edit-menu actions are disabled. */
    public ThenTheDrawing undo_and_redo_are_both_disabled() {
        assertThat(manager.getUndoAction().isEnabled()).as("Undo enabled").isFalse();
        assertThat(manager.getRedoAction().isEnabled()).as("Redo enabled").isFalse();
        return self();
    }

    /** AC4: the Undo menu label carries the presentation name of the next edit. */
    public ThenTheDrawing the_undo_menu_item_is_labelled_with_$(@Quoted String text) {
        Object label = manager.getUndoAction().getValue(Action.NAME);
        assertThat(manager.getUndoAction().isEnabled()).as("Undo enabled").isTrue();
        assertThat(String.valueOf(label)).contains(text);
        return self();
    }

    /** AC5: the other open view is untouched and keeps its own history. */
    public ThenTheDrawing the_other_drawing_is_unaffected() {
        verify(secondaryEdit, never()).undo();
        assertThat(secondManager.canUndo()).as("other view can still undo").isTrue();
        assertThat(secondManager.canRedo()).as("other view has nothing to redo").isFalse();
        return self();
    }

    /** AC6: a significant edit marks the view as having unsaved changes. */
    public ThenTheDrawing the_view_is_marked_as_having_unsaved_changes() {
        assertThat(manager.hasSignificantEdits()).isTrue();
        return self();
    }

    /** AC6 (boundary): an insignificant edit leaves the view "clean". */
    public ThenTheDrawing the_view_is_not_marked_as_having_unsaved_changes() {
        assertThat(manager.hasSignificantEdits()).isFalse();
        return self();
    }

    /** AC7: performing a new edit after undos discards the redo stack. */
    public ThenTheDrawing the_redo_stack_is_cleared() {
        assertThat(manager.canRedo()).as("redo stack after a new edit").isFalse();
        assertThat(manager.canUndo()).as("the new edit is itself undoable").isTrue();
        return self();
    }
}
