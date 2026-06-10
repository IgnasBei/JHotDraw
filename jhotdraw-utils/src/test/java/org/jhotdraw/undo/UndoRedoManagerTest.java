/*
 * @(#)UndoRedoManagerTest.java
 *
 * Unit tests for UndoRedoManager — the core domain class of the
 * Undo/Redo feature in the JHotDraw case study.
 *
 * Style: JUnit 4 (per L5 TestLab1 brief — "Swing and JUnit extensions
 * often work best with JUnit 4"). Mocks created with Mockito 4.x.
 */
package org.jhotdraw.undo;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.Action;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behaviour-driven test suite for {@link UndoRedoManager}.
 *
 * <p>Each test follows Arrange / Act / Assert. Where the unit under test
 * (a single method on the manager) reaches into a collaborator
 * (the {@link UndoableEdit}), the collaborator is a Mockito mock — the
 * lab brief explicitly says: <em>"When the execution of a method passes
 * outside of that method, you have a dependency and should apply
 * mocks/stubs to avoid the dependency."</em>
 */
public class UndoRedoManagerTest {

    private UndoRedoManager manager;

    @Before
    public void setUp() {
        manager = new UndoRedoManager();
    }

    // ---------------------------------------------------------------
    //  Best-case scenarios
    // ---------------------------------------------------------------

    /** Brand-new manager: nothing on either stack, both menu actions disabled. */
    @Test
    public void freshManager_hasNoEditsAndDisabledMenuActions() {
        assertFalse("fresh manager must not be able to undo", manager.canUndo());
        assertFalse("fresh manager must not be able to redo", manager.canRedo());
        assertFalse("fresh manager must not have significant edits",
                manager.hasSignificantEdits());

        Action undoAction = manager.getUndoAction();
        Action redoAction = manager.getRedoAction();
        assertNotNull(undoAction);
        assertNotNull(redoAction);
        assertFalse("undo action must start disabled", undoAction.isEnabled());
        assertFalse("redo action must start disabled", redoAction.isEnabled());
    }

    /** Adding one significant edit puts it on the undo stack and enables Undo. */
    @Test
    public void addEdit_significant_makesUndoableAndEnablesUndoAction() {
        UndoableEdit edit = newSignificantEdit();

        boolean accepted = manager.addEdit(edit);

        assertTrue("addEdit must accept a fresh edit", accepted);
        assertTrue(manager.canUndo());
        assertFalse("nothing has been undone yet", manager.canRedo());
        assertTrue("a significant edit was added, hasSignificantEdits must be true",
                manager.hasSignificantEdits());
        assertTrue("undo menu action must become enabled",
                manager.getUndoAction().isEnabled());
    }

    /** undo() pops the top edit, leaving the stack ready for redo. */
    @Test
    public void undo_movesEditFromUndoStackToRedoStack() {
        UndoableEdit edit = newSignificantEdit();
        manager.addEdit(edit);

        manager.undo();

        verify(edit, times(1)).undo();
        assertFalse("after undo there is nothing left to undo", manager.canUndo());
        assertTrue("after undo the edit is on the redo stack", manager.canRedo());
        assertTrue("redo menu action must be enabled after an undo",
                manager.getRedoAction().isEnabled());
    }

    /** redo() replays the last undone edit. */
    @Test
    public void redo_replaysEdit() {
        UndoableEdit edit = newSignificantEdit();
        manager.addEdit(edit);
        manager.undo();

        manager.redo();

        verify(edit, times(1)).redo();
        assertTrue("after redo there is again something to undo", manager.canUndo());
        assertFalse("after redo the redo stack is empty", manager.canRedo());
    }

    /** undoOrRedo() picks the right operation depending on stack state. */
    @Test
    public void undoOrRedo_picksUndoFirst_thenRedo() {
        UndoableEdit edit = newSignificantEdit();
        manager.addEdit(edit);

        manager.undoOrRedo(); // should undo

        verify(edit, times(1)).undo();
        verify(edit, never()).redo();
        assertTrue(manager.canRedo());

        manager.undoOrRedo(); // should redo

        verify(edit, times(1)).redo();
        assertTrue(manager.canUndo());
    }

    /** discardAllEdits() empties both stacks and clears the significance flag. */
    @Test
    public void discardAllEdits_clearsStateAndSignificance() {
        manager.addEdit(newSignificantEdit());
        manager.addEdit(newSignificantEdit());
        assertTrue(manager.canUndo());
        assertTrue(manager.hasSignificantEdits());

        manager.discardAllEdits();

        assertFalse(manager.canUndo());
        assertFalse(manager.canRedo());
        assertFalse(manager.hasSignificantEdits());
        assertFalse("undo menu action must be disabled after discard",
                manager.getUndoAction().isEnabled());
    }

    /**
     * Insignificant edits do not flip {@code hasSignificantEdits}.
     *
     * <p>Note: {@link javax.swing.undo.UndoManager#editToBeUndone()} skips
     * insignificant edits — they are designed to ride along with the next
     * significant edit. So we assert on the acceptance and the
     * significance flag rather than on {@code canUndo()}.</p>
     */
    @Test
    public void addEdit_insignificant_doesNotMarkSignificant() {
        UndoableEdit edit = newInsignificantEdit();

        boolean accepted = manager.addEdit(edit);

        assertTrue("addEdit must accept the insignificant edit", accepted);
        assertFalse("an insignificant edit must not flip hasSignificantEdits",
                manager.hasSignificantEdits());
    }

    /** PropertyChangeListener fires with the new value when significance flips. */
    @Test
    public void setHasSignificantEdits_firesPropertyChange() {
        RecordingListener listener = new RecordingListener();
        manager.addPropertyChangeListener("hasSignificantEdits", listener);

        manager.setHasSignificantEdits(true);

        assertEquals(1, listener.events.size());
        PropertyChangeEvent evt = listener.events.get(0);
        assertEquals("hasSignificantEdits", evt.getPropertyName());
        assertEquals(Boolean.FALSE, evt.getOldValue());
        assertEquals(Boolean.TRUE, evt.getNewValue());
    }

    // ---------------------------------------------------------------
    //  Boundary scenarios
    // ---------------------------------------------------------------

    /** undo() on an empty manager throws CannotUndoException. */
    @Test(expected = CannotUndoException.class)
    public void undo_onEmptyManager_throws() {
        manager.undo();
    }

    /** redo() on a manager with nothing undone throws CannotRedoException. */
    @Test(expected = CannotRedoException.class)
    public void redo_withoutUndo_throws() {
        manager.redo();
    }

    /**
     * Re-entrancy boundary: while {@code undoOrRedoInProgress} is true the
     * manager must drop incoming edits and call {@code die()} on them so they
     * release any resources. This is the central invariant that prevents
     * listener fan-out during an undo from re-pushing events back onto the stack.
     */
    @Test
    public void addEditDuringUndo_isIgnoredAndEditIsDied() {
        // Arrange: a primary edit whose undo() fires a listener that tries
        // to add a secondary edit back to the manager (the typical Swing
        // wiring that the guard exists to neutralise).
        UndoableEdit secondary = newSignificantEdit();
        UndoableEdit primary = newSignificantEdit();
        // simulate the model firing a re-entrant edit while undoing
        org.mockito.stubbing.Answer<Void> reentrantFire = invocation -> {
            manager.addEdit(secondary);
            return null;
        };
        org.mockito.Mockito.doAnswer(reentrantFire).when(primary).undo();

        manager.addEdit(primary);

        // Act
        manager.undo();

        // Assert: secondary was rejected mid-undo and got die()'d
        verify(secondary, times(1)).die();
        // primary is now on the redo stack — secondary must NOT be there
        assertTrue("primary edit is on the redo stack", manager.canRedo());
        // canUndo must be false: only primary existed and it was just undone
        assertFalse("secondary must not have been added during the undo",
                manager.canUndo());
    }

    /**
     * Boundary: when undo() throws (the underlying edit refuses), the manager
     * must still clear the {@code undoOrRedoInProgress} flag. We verify
     * this indirectly: after the failure a new addEdit() must be accepted
     * (it would be died and ignored if the flag were still set).
     */
    @Test
    public void undo_resetsGuardEvenWhenUndoableEditThrows() {
        UndoableEdit failing = mock(UndoableEdit.class);
        when(failing.canUndo()).thenReturn(true);
        when(failing.isSignificant()).thenReturn(true);
        org.mockito.Mockito.doThrow(new CannotUndoException()).when(failing).undo();
        manager.addEdit(failing);

        try {
            manager.undo();
            fail("undo should have propagated CannotUndoException");
        } catch (CannotUndoException expected) {
            // expected
        }

        // If the guard were stuck on, this edit would be died and ignored.
        UndoableEdit recovery = newSignificantEdit();
        manager.addEdit(recovery);
        verify(recovery, never()).die();
        assertTrue("manager must accept a new edit after a failed undo",
                manager.canUndo());
    }

    /**
     * The disabled-state menu label falls back to the static resource key
     * (boundary: empty stack). The enabled-state label is taken from
     * {@code getUndoPresentationName()}, so the *fallback* path is the
     * one worth pinning.
     */
    @Test
    public void undoMenuActionLabel_isPresentBothEmptyAndPopulated() {
        // Empty stack → fallback label
        Object emptyLabel = manager.getUndoAction().getValue(Action.NAME);
        assertNotNull("empty-stack undo action must have a label", emptyLabel);
        assertFalse("empty-stack undo action must be disabled",
                manager.getUndoAction().isEnabled());

        // After adding an edit → presentation-name label
        manager.addEdit(newSignificantEdit());
        Object populatedLabel = manager.getUndoAction().getValue(Action.NAME);
        assertNotNull("populated-stack undo action must have a label",
                populatedLabel);
        assertTrue(manager.getUndoAction().isEnabled());
    }

    /**
     * Removing a property change listener must actually unsubscribe it.
     * (Boundary case for the listener-management API surface.)
     */
    @Test
    public void removePropertyChangeListener_actuallyUnsubscribes() {
        RecordingListener listener = new RecordingListener();
        manager.addPropertyChangeListener("hasSignificantEdits", listener);
        manager.removePropertyChangeListener("hasSignificantEdits", listener);

        manager.setHasSignificantEdits(true);

        assertTrue("listener must not receive events after removal",
                listener.events.isEmpty());
    }

    // ---------------------------------------------------------------
    //  Test fixtures
    // ---------------------------------------------------------------

    /**
     * A mocked UndoableEdit that the manager will treat as a normal,
     * significant edit: can be undone, can be redone, is significant.
     * Default Mockito returns are <code>false</code> for booleans and
     * <code>null</code> for object methods, which means the parent
     * {@code CompoundEdit.addEdit} will neither merge nor replace it —
     * it will simply be appended, which is exactly what we want.
     */
    private UndoableEdit newSignificantEdit() {
        UndoableEdit edit = mock(UndoableEdit.class);
        when(edit.canUndo()).thenReturn(true);
        when(edit.canRedo()).thenReturn(true);
        when(edit.isSignificant()).thenReturn(true);
        when(edit.getPresentationName()).thenReturn("Mock Edit");
        when(edit.getUndoPresentationName()).thenReturn("Undo Mock Edit");
        when(edit.getRedoPresentationName()).thenReturn("Redo Mock Edit");
        return edit;
    }

    /** Same as {@link #newSignificantEdit()} but with isSignificant == false. */
    private UndoableEdit newInsignificantEdit() {
        UndoableEdit edit = mock(UndoableEdit.class);
        when(edit.canUndo()).thenReturn(true);
        when(edit.canRedo()).thenReturn(true);
        when(edit.isSignificant()).thenReturn(false);
        when(edit.getPresentationName()).thenReturn("Insignificant Mock");
        return edit;
    }

    /** Captures every property change event so the test can assert against them. */
    private static final class RecordingListener implements PropertyChangeListener {
        final java.util.List<PropertyChangeEvent> events = new java.util.ArrayList<>();
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            events.add(evt);
        }
    }
}
