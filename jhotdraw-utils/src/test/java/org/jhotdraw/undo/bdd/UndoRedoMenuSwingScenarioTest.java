/*
 * @(#)UndoRedoMenuSwingScenarioTest.java
 *
 * L6 TestLab2 (BDD), Swing side. The lab brief says: "For Swing applications
 * use the AssertJ-swing to automate the Scenarios." This test drives a real
 * Swing Edit menu whose Undo/Redo items are backed by UndoRedoManager's
 * Actions, and asserts — through an AssertJ-Swing FrameFixture — that the menu
 * reflects the manager state (acceptance criteria AC1, AC3, AC4).
 *
 * The scenario needs a display, so it skips cleanly on a headless CI box via
 * an Assume guard; on a desktop it runs against a short-lived window.
 */
package org.jhotdraw.undo.bdd;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.undo.UndoableEdit;

import org.jhotdraw.undo.UndoRedoManager;

import org.assertj.swing.edt.FailOnThreadViolationRepaintManager;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.edt.GuiTask;
import org.assertj.swing.fixture.FrameFixture;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AssertJ-Swing scenarios for the Edit -> Undo / Edit -> Redo menu items.
 */
public class UndoRedoMenuSwingScenarioTest {

    private UndoRedoManager manager;
    private FrameFixture window;

    @BeforeClass
    public static void installEdtChecks() {
        Assume.assumeFalse("Headless environment: skipping AssertJ-Swing scenario",
                GraphicsEnvironment.isHeadless());
        FailOnThreadViolationRepaintManager.install();
    }

    @AfterClass
    public static void uninstallEdtChecks() {
        if (!GraphicsEnvironment.isHeadless()) {
            FailOnThreadViolationRepaintManager.uninstall();
        }
    }

    @Before
    public void buildEditMenu() {
        // GIVEN: a drawing window whose Edit menu is wired to a fresh manager.
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                return createDrawingWindow();
            }
        });
        window = new FrameFixture(frame);
        window.show();
    }

    @After
    public void disposeWindow() {
        if (window != null) {
            window.cleanUp();
        }
    }

    /** AC3: a freshly opened drawing shows Undo and Redo greyed out. */
    @Test
    public void fresh_drawing_disables_both_menu_items() {
        // THEN
        window.menuItem("undoMenuItem").requireDisabled();
        window.menuItem("redoMenuItem").requireDisabled();
    }

    /** AC1 + AC4: a significant edit enables Undo and re-labels it. */
    @Test
    public void a_significant_edit_enables_and_labels_the_undo_item() {
        // WHEN
        addEditOnEdt(MockEdits.significant("Move Figure"));
        // THEN: the fixture verifies enabled state, AssertJ verifies the label.
        window.menuItem("undoMenuItem").requireEnabled();
        window.menuItem("redoMenuItem").requireDisabled();
        assertThat(undoMenuItemText()).isEqualTo("Undo Move Figure");
    }

    /** AC1 -> AC2 transition: undoing enables Redo and disables Undo again. */
    @Test
    public void undoing_enables_the_redo_item() {
        // GIVEN a significant edit, WHEN the user undoes it
        addEditOnEdt(MockEdits.significant("Move Figure"));
        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                manager.undo();
            }
        });
        // THEN
        window.menuItem("undoMenuItem").requireDisabled();
        window.menuItem("redoMenuItem").requireEnabled();
    }

    // ---------------------------------------------------------------
    //  Helpers (all Swing state is touched on the EDT)
    // ---------------------------------------------------------------

    private JFrame createDrawingWindow() {
        manager = new UndoRedoManager();
        JMenuItem undoItem = new JMenuItem(manager.getUndoAction());
        undoItem.setName("undoMenuItem");
        JMenuItem redoItem = new JMenuItem(manager.getRedoAction());
        redoItem.setName("redoMenuItem");
        JMenu editMenu = new JMenu("Edit");
        editMenu.setName("editMenu");
        editMenu.add(undoItem);
        editMenu.add(redoItem);
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(editMenu);
        JFrame frame = new JFrame("Undo/Redo BDD");
        frame.setName("undoRedoFrame");
        frame.setJMenuBar(menuBar);
        frame.setPreferredSize(new Dimension(240, 100));
        frame.pack();
        return frame;
    }

    private String undoMenuItemText() {
        return GuiActionRunner.execute(new GuiQuery<String>() {
            @Override
            protected String executeInEDT() {
                return window.menuItem("undoMenuItem").target().getText();
            }
        });
    }

    private void addEditOnEdt(final UndoableEdit edit) {
        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                manager.addEdit(edit);
            }
        });
    }
}
