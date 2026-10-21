/*
 * @(#)DrawViewUndoWiringTest.java
 *
 * Integration test for the Undo/Redo wiring in the Draw sample.
 *
 * UndoRedoManagerTest and the BDD scenarios test the manager on its own,
 * with wiring they set up themselves. They all passed while Edit > Undo did
 * nothing in the running application: AbstractApplication.createView()
 * replaced the view's action map after DrawView had put its undo/redo
 * actions there. This test builds the view through the real application,
 * the same way Main does, so that wiring is covered too.
 *
 * Style: JUnit 4. No window is shown, so it also runs headless in CI.
 */
package org.jhotdraw.samples.draw;

import java.awt.geom.Point2D;
import javax.swing.Action;
import javax.swing.undo.AbstractUndoableEdit;

import org.jhotdraw.action.edit.RedoAction;
import org.jhotdraw.action.edit.UndoAction;
import org.jhotdraw.api.app.View;
import org.jhotdraw.app.SDIApplication;
import org.jhotdraw.draw.Drawing;
import org.jhotdraw.draw.figure.Figure;
import org.jhotdraw.draw.figure.RectangleFigure;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DrawViewUndoWiringTest {

    private View view;
    private Drawing drawing;

    @Before
    public void setUp() {
        // Arrange: the same application set-up as Main, minus launch().
        DrawApplicationModel model = new DrawApplicationModel();
        model.setName("JHotDraw Draw");
        model.setViewClassName("org.jhotdraw.samples.draw.DrawView");
        SDIApplication app = new SDIApplication();
        app.setModel(model);
        app.init();
        view = app.createView();
        app.add(view);
        drawing = ((DrawView) view).getEditor().getDrawingViews().iterator().next().getDrawing();
    }

    /** Adds a figure and reports it as an undoable edit, like CreationTool does. */
    private void createRectangle() {
        final Figure figure = new RectangleFigure();
        figure.setBounds(new Point2D.Double(10, 10), new Point2D.Double(50, 40));
        drawing.add(figure);
        drawing.fireUndoableEditHappened(new AbstractUndoableEdit() {
            private static final long serialVersionUID = 1L;

            @Override
            public String getPresentationName() {
                return "Create Figure";
            }

            @Override
            public void undo() {
                super.undo();
                drawing.remove(figure);
            }

            @Override
            public void redo() {
                super.redo();
                drawing.add(figure);
            }
        });
    }

    @Test
    public void undoActionOfTheViewUndoesAnEditOnItsDrawing() {
        createRectangle();
        Action undo = view.getActionMap().get(UndoAction.ID);

        assertTrue("Undo should be enabled after an edit", undo.isEnabled());
        undo.actionPerformed(null);

        assertEquals("Undo should remove the created figure", 0, drawing.getChildCount());
    }

    @Test
    public void redoActionOfTheViewRedoesAnUndoneEdit() {
        createRectangle();
        view.getActionMap().get(UndoAction.ID).actionPerformed(null);
        assertEquals("Precondition: the figure was undone", 0, drawing.getChildCount());
        Action redo = view.getActionMap().get(RedoAction.ID);

        assertTrue("Redo should be enabled after an undo", redo.isEnabled());
        redo.actionPerformed(null);

        assertEquals("Redo should bring the figure back", 1, drawing.getChildCount());
    }

    @Test
    public void undoMenuLabelNamesTheEditToBeUndone() {
        createRectangle();

        assertEquals("Undo Create Figure",
                view.getActionMap().get(UndoAction.ID).getValue(Action.NAME));
        assertFalse(view.getActionMap().get(RedoAction.ID).isEnabled());
    }
}
