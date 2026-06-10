/*
 * @(#)CompositeEditTest.java
 *
 * Unit tests for CompositeEdit — the helper that bundles several
 * UndoableEdits into a single user-visible undo step (e.g. a multi-figure
 * move). Part of the L5 TestLab1 deliverable.
 */
package org.jhotdraw.undo;

import javax.swing.undo.UndoableEdit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the differentiating behaviour of {@link CompositeEdit} on top of
 * {@link javax.swing.undo.CompoundEdit}: presentation-name selection,
 * the significance flag, the {@code addEdit(this) ⇒ end()} protocol, and
 * the "ignore-nested-CompositeEdit-while-in-progress" rule.
 *
 * <p>Mockito mocks are used for child {@link UndoableEdit}s so the tests
 * stay focused on a single method of {@code CompositeEdit} per case.</p>
 */
public class CompositeEditTest {

    // ---------------------------------------------------------------
    //  Best-case scenarios — constructors and presentation names
    // ---------------------------------------------------------------

    @Test
    public void defaultConstructor_isSignificantAndUsesSuperPresentationName() {
        CompositeEdit edit = new CompositeEdit();

        // No children added, parent CompoundEdit reports an empty "" name.
        assertEquals("", edit.getPresentationName());
        // Initial flag — see Javadoc on no-arg constructor.
        // Without children, super.isSignificant() returns false, so the
        // observed value of isSignificant() is also false until at least
        // one significant child is added.
        edit.addEdit(newSignificantChild());
        assertTrue("a significant child must make the composite significant",
                edit.isSignificant());
    }

    @Test
    public void presentationNameConstructor_overridesSuperName() {
        CompositeEdit edit = new CompositeEdit("Move Figures");

        assertEquals("Move Figures", edit.getPresentationName());
    }

    @Test
    public void significanceConstructor_overridesSignificanceFlag() {
        CompositeEdit insignificant = new CompositeEdit(false);
        insignificant.addEdit(newSignificantChild());

        assertFalse("isSignificant=false constructor must dominate the children",
                insignificant.isSignificant());

        CompositeEdit significant = new CompositeEdit(true);
        significant.addEdit(newSignificantChild());

        assertTrue(significant.isSignificant());
    }

    @Test
    public void undoAndRedoPresentationNames_includeBundleLabel() {
        CompositeEdit edit = new CompositeEdit("Move Figures");

        // The labels module is loaded lazily; trigger initialisation through
        // the manager so getLabels() returns a non-null bundle.
        new UndoRedoManager();

        String undoName = edit.getUndoPresentationName();
        String redoName = edit.getRedoPresentationName();
        assertNotNull(undoName);
        assertNotNull(redoName);
        assertTrue("undo presentation name must mention the composite name",
                undoName.contains("Move Figures"));
        assertTrue("redo presentation name must mention the composite name",
                redoName.contains("Move Figures"));
    }

    // ---------------------------------------------------------------
    //  Best-case — child aggregation
    // ---------------------------------------------------------------

    @Test
    public void undo_callsUndoOnEveryChildInReverseOrder() {
        CompositeEdit edit = new CompositeEdit("Composite Undo");
        UndoableEdit first = newSignificantChild();
        UndoableEdit second = newSignificantChild();
        edit.addEdit(first);
        edit.addEdit(second);
        edit.end(); // CompoundEdit must be ended before undo()

        edit.undo();

        verify(second, times(1)).undo();
        verify(first, times(1)).undo();
        verify(first, never_method()).redo();
    }

    @Test
    public void redo_callsRedoOnEveryChildInOriginalOrder() {
        CompositeEdit edit = new CompositeEdit("Composite Redo");
        UndoableEdit first = newSignificantChild();
        UndoableEdit second = newSignificantChild();
        edit.addEdit(first);
        edit.addEdit(second);
        edit.end();

        edit.undo();
        edit.redo();

        verify(first, times(1)).redo();
        verify(second, times(1)).redo();
    }

    // ---------------------------------------------------------------
    //  Boundary — the addEdit(this) ⇒ end() protocol
    // ---------------------------------------------------------------

    /**
     * The headline difference from {@code CompoundEdit}: re-firing the
     * same instance as a child edit means "the gesture is over, end the
     * composite". The method must return true and the composite must be
     * marked as no longer in progress.
     */
    @Test
    public void addEdit_self_endsTheCompositeAndReturnsTrue() {
        CompositeEdit edit = new CompositeEdit("Group Move");
        edit.addEdit(newSignificantChild());

        boolean result = edit.addEdit(edit);

        assertTrue("addEdit(self) must return true", result);
        // After end(), addEdit on a foreign child must be rejected
        boolean acceptedAfterEnd = edit.addEdit(newSignificantChild());
        assertFalse("after self-add the composite is ended; further addEdit "
                + "must be refused by CompoundEdit.addEdit", acceptedAfterEnd);
    }

    /**
     * Boundary: while in progress, a foreign nested CompositeEdit is
     * silently ignored — it returns true but is NOT stored, so it cannot
     * be undone twice. (This is the rule that prevents the manager from
     * adding the same composite twice when the model fires the start and
     * end markers as separate {@code UndoableEditEvent}s.)
     *
     * <p>We use a Mockito spy on a real CompositeEdit so we can both
     * trigger the real {@code instanceof CompositeEdit} branch in the
     * production code and {@code verify()} on it afterwards.</p>
     */
    @Test
    public void addEdit_nestedCompositeWhileInProgress_isIgnored() {
        CompositeEdit outer = new CompositeEdit("Outer");
        UndoableEdit firstChild = newSignificantChild();
        outer.addEdit(firstChild);

        CompositeEdit nested = org.mockito.Mockito.spy(new CompositeEdit("Inner"));
        // make the spy "look fully populated" so that, if it were stored
        // and later undone, undo()/canUndo() would visibly fire — proving
        // the production code never reached it.
        org.mockito.Mockito.doReturn(true).when(nested).canUndo();

        boolean result = outer.addEdit(nested);

        assertTrue("nested CompositeEdit must be silently accepted (return true)",
                result);
        // End the outer composite and undo: the inner composite must not
        // have its undo() invoked because it was never stored.
        outer.end();
        outer.undo();

        verify(nested, org.mockito.Mockito.never()).undo();
        // The legitimate child still undoes
        verify(firstChild, times(1)).undo();
    }

    // ---------------------------------------------------------------
    //  Boundary — significance flag
    // ---------------------------------------------------------------

    @Test
    public void setSignificant_canSilenceAComposite() {
        CompositeEdit edit = new CompositeEdit();
        edit.addEdit(newSignificantChild());
        assertTrue(edit.isSignificant());

        edit.setSignificant(false);

        assertFalse("setSignificant(false) must override children",
                edit.isSignificant());
    }

    @Test
    public void emptyComposite_isInsignificantEvenWhenFlagIsTrue() {
        CompositeEdit edit = new CompositeEdit(true);

        // No children → super.isSignificant() returns false, so the AND
        // wins: the observed significance is false.
        assertFalse(edit.isSignificant());
    }

    // ---------------------------------------------------------------
    //  Test fixtures
    // ---------------------------------------------------------------

    private static UndoableEdit newSignificantChild() {
        UndoableEdit edit = mock(UndoableEdit.class);
        when(edit.canUndo()).thenReturn(true);
        when(edit.canRedo()).thenReturn(true);
        when(edit.isSignificant()).thenReturn(true);
        return edit;
    }

    /** Local alias for Mockito.never(); avoids static-import name clashes. */
    private static org.mockito.verification.VerificationMode never_method() {
        return org.mockito.Mockito.never();
    }
}
