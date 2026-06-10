/*
 * @(#)AbstractUndoRedoViewAction.java
 *
 * Copyright (c) 1996-2010 The authors and contributors of JHotDraw.
 * You may not use, copy or modify this file, except in compliance with the
 * accompanying license terms.
 */
package org.jhotdraw.action.edit;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.jhotdraw.action.AbstractViewAction;
import org.jhotdraw.api.app.Application;
import org.jhotdraw.api.app.View;
import org.jhotdraw.util.ResourceBundleUtil;

/**
 * Common base class for {@link UndoAction} and {@link RedoAction}.
 *
 * <p>Both actions share the same algorithm: locate the per-view "real"
 * action published in {@code View.getActionMap()} under a fixed id,
 * mirror its enabled state and presentation name onto this menu-level
 * action, and delegate {@code actionPerformed} to it. The subclasses only
 * differ in the action id and the resource key, so this base class
 * implements the algorithm as a Template Method and the subclasses fill
 * in the id via {@link #getActionId()}.</p>
 */
abstract class AbstractUndoRedoViewAction extends AbstractViewAction {

    private static final long serialVersionUID = 1L;

    protected final ResourceBundleUtil labels =
            ResourceBundleUtil.getBundle("org.jhotdraw.action.Labels");

    private final PropertyChangeListener realActionListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String name = evt.getPropertyName();
            if ((name == null && AbstractAction.NAME == null)
                    || (name != null && name.equals(AbstractAction.NAME))) {
                putValue(AbstractAction.NAME, evt.getNewValue());
            } else if ("enabled".equals(name)) {
                updateEnabledState();
            }
        }
    };

    protected AbstractUndoRedoViewAction(Application app, View view) {
        super(app, view);
        labels.configureAction(this, getActionId());
    }

    /** The id under which the real per-view action is published. */
    protected abstract String getActionId();

    protected void updateEnabledState() {
        Action realAction = getRealAction();
        boolean isEnabled = (realAction != null && realAction != this)
                && realAction.isEnabled();
        setEnabled(isEnabled);
    }

    @Override
    protected void updateView(View oldValue, View newValue) {
        super.updateView(oldValue, newValue);
        if (newValue != null) {
            Action realAction = newValue.getActionMap().get(getActionId());
            if (realAction != null && realAction != this) {
                putValue(AbstractAction.NAME, realAction.getValue(AbstractAction.NAME));
                updateEnabledState();
            }
        }
    }

    @Override
    protected void installViewListeners(View p) {
        super.installViewListeners(p);
        Action realAction = p.getActionMap().get(getActionId());
        if (realAction != null && realAction != this) {
            realAction.addPropertyChangeListener(realActionListener);
        }
    }

    @Override
    protected void uninstallViewListeners(View p) {
        super.uninstallViewListeners(p);
        Action realAction = p.getActionMap().get(getActionId());
        if (realAction != null && realAction != this) {
            realAction.removePropertyChangeListener(realActionListener);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Action realAction = getRealAction();
        if (realAction != null && realAction != this) {
            realAction.actionPerformed(e);
        }
    }

    private Action getRealAction() {
        return (getActiveView() == null)
                ? null
                : getActiveView().getActionMap().get(getActionId());
    }
}
