/*
 * @(#)UndoRedoScenarioTest.java
 *
 * L6 TestLab2 (BDD). The user story "As a user editing a drawing I want to
 * undo my last action and, if I change my mind, redo it" (ChangeReqLab1) is
 * mapped here to Given-When-Then scenarios and automated with JGiven. Each
 * @Test corresponds to one acceptance criterion from the user story.
 *
 * Assertions on the domain (UndoRedoManager) use the AssertJ library; the
 * Swing Edit-menu actions are covered separately by UndoRedoMenuSwingScenarioTest.
 */
package org.jhotdraw.undo.bdd;

import org.junit.Test;

import com.tngtech.jgiven.annotation.As;
import com.tngtech.jgiven.junit.ScenarioTest;

/**
 * BDD scenarios for the Undo/Redo feature, one per acceptance criterion.
 */
public class UndoRedoScenarioTest
        extends ScenarioTest<GivenAnEditableDrawing, WhenTheUser, ThenTheDrawing> {

    /** AC1: undo reverses the most recent edit. */
    @Test
    @As("AC1: Undo reverses the most recent edit")
    public void undo_reverses_the_most_recent_edit() {
        given().an_open_drawing()
                .and().a_significant_edit_named_$("Move Figure");
        when().invokes_undo();
        then().the_most_recent_edit_is_reversed();
    }

    /** AC2: redo re-applies an edit that was just undone. */
    @Test
    @As("AC2: Redo re-applies an undone edit")
    public void redo_reapplies_an_undone_edit() {
        given().an_open_drawing()
                .and().a_significant_edit_named_$("Move Figure")
                .and().the_edit_has_already_been_undone();
        when().invokes_redo();
        then().the_edit_is_re_applied();
    }

    /** AC3: a freshly opened drawing has Undo and Redo disabled. */
    @Test
    @As("AC3: A fresh drawing has Undo and Redo disabled")
    public void fresh_drawing_has_undo_and_redo_disabled() {
        given().an_open_drawing();
        then().undo_and_redo_are_both_disabled();
    }

    /** AC4: the Undo menu item shows the presentation name of the next edit. */
    @Test
    @As("AC4: The Undo menu item is labelled with the next edit")
    public void undo_menu_item_shows_the_presentation_name() {
        given().an_open_drawing()
                .and().a_significant_edit_named_$("Move Figure");
        then().the_undo_menu_item_is_labelled_with_$("Move Figure");
    }

    /** AC5: undoing in one view does not affect another open view. */
    @Test
    @As("AC5: Undo is independent per view")
    public void undo_is_independent_per_view() {
        given().an_open_drawing()
                .and().a_significant_edit_named_$("Move Figure")
                .and().a_second_independent_drawing_with_its_own_edit();
        when().invokes_undo();
        then().the_most_recent_edit_is_reversed()
                .and().the_other_drawing_is_unaffected();
    }

    /** AC6: a significant edit marks the view as having unsaved changes. */
    @Test
    @As("AC6: A significant edit marks unsaved changes")
    public void significant_edit_marks_unsaved_changes() {
        given().an_open_drawing()
                .and().a_significant_edit_named_$("Change Fill Color");
        then().the_view_is_marked_as_having_unsaved_changes();
    }

    /** AC6 (boundary): an insignificant edit does not mark unsaved changes. */
    @Test
    @As("AC6 (boundary): An insignificant edit leaves the view clean")
    public void insignificant_edit_does_not_mark_unsaved_changes() {
        given().an_open_drawing()
                .and().an_insignificant_edit();
        then().the_view_is_not_marked_as_having_unsaved_changes();
    }

    /** AC7: performing a new edit after undos clears the redo stack. */
    @Test
    @As("AC7: A new edit after undos clears the redo stack")
    public void new_edit_after_undos_clears_redo_stack() {
        given().an_open_drawing()
                .and().a_significant_edit_named_$("Move Figure")
                .and().the_edit_has_already_been_undone();
        when().performs_a_new_$_edit("Draw Rectangle");
        then().the_redo_stack_is_cleared();
    }
}
