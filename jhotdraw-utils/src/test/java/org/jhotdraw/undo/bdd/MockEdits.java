/*
 * @(#)MockEdits.java
 *
 * L6 TestLab2 (BDD) test-support: factory for mocked UndoableEdits so the
 * JGiven stages do not reach into a real Swing edit. Same single-method /
 * single-path discipline as the L5 TestLab1 unit tests: collaborators of
 * the unit under test (UndoRedoManager) are Mockito mocks.
 */
package org.jhotdraw.undo.bdd;

import javax.swing.undo.UndoableEdit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builders for the {@link UndoableEdit} test doubles used by the
 * Undo/Redo BDD scenarios.
 */
final class MockEdits {

    private MockEdits() {
    }

    /**
     * A significant, undoable, redoable edit whose presentation names are
     * derived from {@code name} (e.g. {@code "Undo Move Figure"}). Mockito's
     * default {@code false}/{@code null} returns for the remaining methods mean
     * the parent {@code CompoundEdit.addEdit} simply appends it — neither
     * merging nor replacing — which is exactly the behaviour the scenarios want.
     */
    static UndoableEdit significant(String name) {
        UndoableEdit edit = mock(UndoableEdit.class);
        when(edit.canUndo()).thenReturn(true);
        when(edit.canRedo()).thenReturn(true);
        when(edit.isSignificant()).thenReturn(true);
        when(edit.getPresentationName()).thenReturn(name);
        when(edit.getUndoPresentationName()).thenReturn("Undo " + name);
        when(edit.getRedoPresentationName()).thenReturn("Redo " + name);
        return edit;
    }

    /** Same as {@link #significant(String)} but {@code isSignificant() == false}. */
    static UndoableEdit insignificant() {
        UndoableEdit edit = mock(UndoableEdit.class);
        when(edit.canUndo()).thenReturn(true);
        when(edit.canRedo()).thenReturn(true);
        when(edit.isSignificant()).thenReturn(false);
        when(edit.getPresentationName()).thenReturn("Adjust Selection");
        return edit;
    }
}
