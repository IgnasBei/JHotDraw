/*
 * @(#)UndoRedoManager.java
 *
 * Copyright (c) 1996-2010 The authors and contributors of JHotDraw.
 * You may not use, copy or modify this file, except in compliance with the
 * accompanying license terms.
 */
package org.jhotdraw.undo;

import java.awt.event.*;
import java.beans.*;
import java.util.*;
import javax.swing.*;
import javax.swing.undo.*;
import org.jhotdraw.util.*;

/**
 * Same as javax.swing.UndoManager but provides actions for undo and
 * redo operations.
 *
 * @author Werner Randelshofer
 * @version $Id$
 */
public class UndoRedoManager extends UndoManager { //javax.swing.undo.UndoManager {

    private static final long serialVersionUID = 1L;
    protected PropertyChangeSupport propertySupport = new PropertyChangeSupport(this);
    private static final boolean DEBUG = false;
    /**
     * The resource bundle used for internationalisation.
     */
    private static ResourceBundleUtil labels;
    /**
     * This flag is set to true when at
     * least one significant UndoableEdit
     * has been added to the manager since the
     * last call to discardAllEdits.
     */
    private boolean hasSignificantEdits = false;
    /**
     * This flag is set to true when an undo or redo
     * operation is in progress. The UndoRedoManager
     * ignores all incoming UndoableEdit events while
     * this flag is true.
     */
    private boolean undoOrRedoInProgress;
    /**
     * Sending this UndoableEdit event to the UndoRedoManager
     * disables the Undo and Redo functions of the manager.
     */
    public static final UndoableEdit DISCARD_ALL_EDITS = new AbstractUndoableEdit() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean canUndo() {
            return false;
        }

        @Override
        public boolean canRedo() {
            return false;
        }
    };

    /**
     * Undo Action for use in a menu bar.
     */
    private class UndoAction
            extends AbstractAction {

        private static final long serialVersionUID = 1L;

        public UndoAction() {
            labels.configureAction(this, "edit.undo");
            setEnabled(false);
        }

        /**
         * Invoked when an action occurs.
         */
        @Override
        public void actionPerformed(ActionEvent evt) {
            try {
                undo();
            } catch (CannotUndoException e) {
                System.err.println("Cannot undo: " + e);
                e.printStackTrace();
            }
        }
    }

    /**
     * Redo Action for use in a menu bar.
     */
    private class RedoAction
            extends AbstractAction {

        private static final long serialVersionUID = 1L;

        public RedoAction() {
            labels.configureAction(this, "edit.redo");
            setEnabled(false);
        }

        /**
         * Invoked when an action occurs.
         */
        @Override
        public void actionPerformed(ActionEvent evt) {
            try {
                redo();
            } catch (CannotRedoException e) {
                System.out.println("Cannot redo: " + e);
            }
        }
    }
    /**
     * The undo action instance.
     */
    private UndoAction undoAction;
    /**
     * The redo action instance.
     */
    private RedoAction redoAction;

    public static ResourceBundleUtil getLabels() {
        if (labels == null) {
            labels = ResourceBundleUtil.getBundle("org.jhotdraw.undo.Labels");
        }
        return labels;
    }

    /**
     * Creates new UndoRedoManager
     */
    public UndoRedoManager() {
        getLabels();
        undoAction = new UndoAction();
        redoAction = new RedoAction();
    }

    public void setLocale(Locale l) {
        labels = ResourceBundleUtil.getBundle("org.jhotdraw.undo.Labels", l);
    }

    /**
     * Discards all edits.
     */
    @Override
    public void discardAllEdits() {
        super.discardAllEdits();
        updateActions();
        setHasSignificantEdits(false);
    }

    public void setHasSignificantEdits(boolean newValue) {
        boolean oldValue = hasSignificantEdits;
        hasSignificantEdits = newValue;
        firePropertyChange("hasSignificantEdits", oldValue, newValue);
    }

    /**
     * Returns true if at least one significant UndoableEdit
     * has been added since the last call to discardAllEdits.
     */
    public boolean hasSignificantEdits() {
        return hasSignificantEdits;
    }

    /**
     * If inProgress, inserts anEdit at indexOfNextAdd, and removes
     * any old edits that were at indexOfNextAdd or later. The die
     * method is called on each edit that is removed is sent, in the
     * reverse of the order the edits were added. Updates
     * indexOfNextAdd.
     *
     * <p>
     * If not inProgress, acts as a CompoundEdit</p>
     *
     * <p>
     * Regardless of inProgress, if undoOrRedoInProgress,
     * calls die on each edit that is sent.</p>
     *
     *
     * @see CompoundEdit#end
     * @see CompoundEdit#addEdit
     */
    @Override
    public boolean addEdit(UndoableEdit anEdit) {
        if (DEBUG) {
            System.out.println("UndoRedoManager@" + hashCode() + ".add " + anEdit);
        }
        if (undoOrRedoInProgress) {
            anEdit.die();
            return true;
        }
        boolean success = super.addEdit(anEdit);
        updateActions();
        if (success && anEdit.isSignificant() && editToBeUndone() == anEdit) {
            setHasSignificantEdits(true);
        }
        return success;
    }

    /**
     * Gets the undo action for use as an Undo menu item.
     */
    public Action getUndoAction() {
        return undoAction;
    }

    /**
     * Gets the redo action for use as a Redo menu item.
     */
    public Action getRedoAction() {
        return redoAction;
    }

    /**
     * Updates the properties of the UndoAction
     * and of the RedoAction.
     */
    private void updateActions() {
        if (DEBUG) {
            System.out.println("UndoRedoManager@" + hashCode() + ".updateActions "
                    + editToBeUndone()
                    + " canUndo=" + canUndo() + " canRedo=" + canRedo());
        }
        configureMenuAction(undoAction, canUndo(), getUndoPresentationName(), "edit.undo.text");
        configureMenuAction(redoAction, canRedo(), getRedoPresentationName(), "edit.redo.text");
    }

    /**
     * Synchronises the enabled state and the menu label of one of the inner
     * Swing actions with this manager's state.
     *
     * @param action            the menu action to update (undoAction or redoAction)
     * @param canPerform        whether the corresponding operation is currently possible
     * @param presentationName  label to show when {@code canPerform} is true
     * @param fallbackLabelKey  resource bundle key for the label when disabled
     */
    private void configureMenuAction(AbstractAction action, boolean canPerform,
                                     String presentationName, String fallbackLabelKey) {
        action.setEnabled(canPerform);
        String label = canPerform ? presentationName : labels.getString(fallbackLabelKey);
        action.putValue(Action.NAME, label);
        action.putValue(Action.SHORT_DESCRIPTION, label);
    }

    /**
     * Undoes the last edit event.
     * The UndoRedoManager ignores all incoming UndoableEdit events,
     * while undo is in progress.
     */
    @Override
    public void undo()
            throws CannotUndoException {
        runGuarded(super::undo);
    }

    /**
     * Redoes the last undone edit event.
     * The UndoRedoManager ignores all incoming UndoableEdit events,
     * while redo is in progress.
     */
    @Override
    public void redo()
            throws CannotUndoException {
        runGuarded(super::redo);
    }

    /**
     * Undoes or redoes the last edit event.
     * The UndoRedoManager ignores all incoming UndoableEdit events,
     * while undo or redo is in progress.
     */
    @Override
    public void undoOrRedo()
            throws CannotUndoException, CannotRedoException {
        runGuarded(super::undoOrRedo);
    }

    /**
     * Runs an undo/redo operation while {@link #undoOrRedoInProgress} is set,
     * so that listener fan-out during the operation does not push events back
     * onto the stack. Always restores the flag and refreshes the menu actions,
     * even if the operation throws.
     */
    private void runGuarded(Runnable operation) {
        // Invariant: undo/redo is never re-entrant. If this fires the
        // listener wiring has produced a recursive undo, which the
        // manager is not designed to handle.
        assert !undoOrRedoInProgress : "runGuarded must not be called re-entrantly";
        undoOrRedoInProgress = true;
        try {
            operation.run();
        } finally {
            undoOrRedoInProgress = false;
            updateActions();
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertySupport.addPropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertySupport.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertySupport.removePropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertySupport.removePropertyChangeListener(propertyName, listener);
    }

    protected void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        propertySupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    protected void firePropertyChange(String propertyName, int oldValue, int newValue) {
        propertySupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        propertySupport.firePropertyChange(propertyName, oldValue, newValue);
    }
}
