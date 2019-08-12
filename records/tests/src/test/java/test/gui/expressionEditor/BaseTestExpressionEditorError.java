package test.gui.expressionEditor;

import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.shape.Path;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.SubstringMatcher;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.lexeditor.EditorDisplay;
import records.transformations.expression.CanonicalSpan;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.DisplaySpan;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import test.TestUtil;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
class BaseTestExpressionEditorError extends FXApplicationTest implements ScrollToTrait, ListUtilTrait, ClickTableLocationTrait, PopupTrait
{
    // Checks that errors don't show up while still in the span,
    // but do show up when you move out or when you click ok.
    // If expression has \u0000 as first character, bracket auto-matching is relied upon.
    // If no such character is there, auto-inserted brackets are deleted
    // as they are entered, then typed manually later on.
    @SuppressWarnings({"units", "identifier"})
    void testError(String expression, Error... errors)
    {        
        try
        {
            UnitManager u = new UnitManager();
            TypeManager typeManager = new TypeManager(u);
            List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
            for (int i = 1; i <= 3; i++)
            {
                int iFinal = i;
                columns.add(rs -> new MemoryStringColumn(rs, new ColumnId("S" + iFinal), ImmutableList.of(Either.right("Hi " + iFinal)), ""));
                columns.add(rs -> new MemoryNumericColumn(rs, new ColumnId("ACC" + iFinal), new NumberInfo(u.loadUse("m/s^2")), ImmutableList.of(Either.right(iFinal)), 0));
            }
            MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, typeManager, new EditableRecordSet(columns, () -> 1));

            Region gridNode = TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(6), CellPosition.col(3));
            for (int i = 0; i < 2; i++)
                clickOnItemInBounds(from(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            // Not sure why this doesn't work:
            //clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            clickOn(".id-new-transform");
            clickOn(".id-transform-calculate");
            write("Table1");
            push(KeyCode.ENTER);
            TestUtil.sleep(200);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            for (char c : expression.toCharArray())
            {
                if (c != 0)
                    write(c);
                // Delete auto-matched brackets:
                if (!expression.startsWith("\u0000") && "({[\"".contains("" + c))
                    push(KeyCode.DELETE);
            }
            sleep(200);
            
            if (errors.length == 0)
            {
                assertErrorShowing(false, false);
                // Clicking OK should be fine:
                moveAndDismissPopupsAtPos(point(".ok-button"));
                clickOn(".ok-button");
                sleep(300);
                assertFalse(lookup(".expression-editor").tryQuery().isPresent());
            }
            else
            {
                EditorDisplay editorDisplay = lookup(".editor-display").<EditorDisplay>query();
                List<ErrorDetails> actualErrors = new ArrayList<>(TestUtil.fx(() -> editorDisplay._test_getErrors().stream().filter(e -> e.error.getLength() > 0).collect(Collectors.toList())));
                List<Error> expectedErrors = new ArrayList<>(Arrays.asList(errors));
                assertEquals(Utility.listToString(actualErrors), expectedErrors.size(), actualErrors.size());
                Collections.sort(actualErrors, Comparator.comparing(e -> e.location));
                Collections.sort(expectedErrors, Comparator.comparing(e -> e.location));
                for (int i = 0; i < expectedErrors.size(); i++)
                {
                    assertEquals("Error: " + actualErrors.get(i).error.toPlain(), expectedErrors.get(i).location, actualErrors.get(i).location);
                    MatcherAssert.assertThat(actualErrors.get(i).error.toPlain().toLowerCase(), new MultiSubstringMatcher(expectedErrors.get(i).expectedMessageParts));
                }
                
                // Not necessarily caret pos of the end, if they
                // entered auto-matched brackets.
                @CanonicalLocation int endingCaretPos = TestUtil.fx(() -> editorDisplay.getCaretPosition());

                boolean hasSpanNotContainingEndingPos = Arrays.stream(errors).anyMatch(s -> !s.location.touches(endingCaretPos));
                assertErrorShowing(hasSpanNotContainingEndingPos, false);


                // Can either provoke error by moving caret into a span or by
                // clicking ok first time
                if (hasSpanNotContainingEndingPos && expression.hashCode() % 2 == 0)
                {
                    // Move into span:
                    boolean seenPopup = false;
                    for (int i = 0; i < expression.length(); i++)
                    {
                        push(KeyCode.LEFT);
                        if (isShowingErrorPopup())
                        {
                            seenPopup = true;
                            break;
                        }
                    }
                    assertTrue("Error popup showed somewhere", seenPopup);
                }
                else
                {
                    // Click ok and check dialog remains and error shows up
                    moveAndDismissPopupsAtPos(point(".ok-button"));
                    clickOn(".ok-button");
                    sleep(300);
                    assertTrue("Expression editor still showing", lookup(".expression-editor").tryQuery().isPresent());
                    assertErrorShowing(true, null);
                    assertTrue(lookup(".ok-double-prompt").tryQuery().isPresent());
                }

                actualErrors = new ArrayList<>(TestUtil.fx(() -> editorDisplay._test_getErrors().stream().filter(e -> e.error.getLength() > 0).collect(Collectors.toList())));
                Collections.sort(actualErrors, Comparator.comparing(e -> e.location));

                for (int i = 0; i < expectedErrors.size(); i++)
                {
                    assertEquals("Error: " + actualErrors.get(i).error.toPlain(), expectedErrors.get(i).displayLocation, actualErrors.get(i).displayLocation);
                }
                
                TestUtil.doubleOk(this);
                assertFalse("Expression editor still showing", lookup(".expression-editor").tryQuery().isPresent());
                System.out.println("Closed expression editor, opening again");
                // Show again and check error is showing from the outset:
                clickOn("DestCol");
                sleep(500);
                assertTrue("Expression editor still showing", lookup(".expression-editor").tryQuery().isPresent());
                assertErrorShowing(true, false);
                // Check it shows if you move into it:
                push(KeyCode.TAB);
                push(KeyCode.HOME);
                boolean seenPopup = false;
                for (int i = 0; i < expression.length(); i++)
                {
                    push(KeyCode.RIGHT);
                    if (isShowingErrorPopup())
                    {
                        seenPopup = true;
                        break;
                    }
                }
                assertTrue("Error popup showed somewhere", seenPopup);
                
                TestUtil.doubleOk(this);
                assertFalse("Expression editor still showing", lookup(".expression-editor").tryQuery().isPresent());
            }
            
        }
        catch (Exception e)
        {
            // Test fails regardless, so no harm turning checked exception
            // into unchecked for simpler signatures:
            throw new RuntimeException(e);
        }
    }

    private void assertErrorShowing(boolean underlineShowing, @Nullable Boolean errorPopupShowing)
    {
        Scene dialogScene = TestUtil.fx(() -> getRealFocusedWindow().getScene());
        Collection<Path> errorUnderline = lookup(".expression-editor .error-underline").<Path>queryAll();
        assertEquals("Underline showing", underlineShowing, errorUnderline.size() > 0);
        if (errorPopupShowing != null)
            assertEquals("Popup showing", errorPopupShowing, isShowingErrorPopup());
    }

    private boolean isShowingErrorPopup()
    {
        // Important to check the .error part too, as it may be showing information or a prompt and that's fine:
        return lookup(".expression-info-popup.error").tryQuery().isPresent();
    }
    
    static class Error
    {
        private final CanonicalSpan location;
        private final DisplaySpan displayLocation;
        private final ImmutableList<String> expectedMessageParts;

        public Error(@CanonicalLocation int start, @CanonicalLocation int end, ImmutableList<String> expectedMessageParts)
        {
            this.location = new CanonicalSpan(start, end);
            @SuppressWarnings("units")
            DisplaySpan displayLocation = TestUtil.fx(() -> new DisplaySpan(start, start == end ? end + 1 : end));
            this.displayLocation = displayLocation;
            this.expectedMessageParts = expectedMessageParts;
        }

        public Error(@CanonicalLocation int start, @CanonicalLocation int end, @DisplayLocation int displayStart, @DisplayLocation int displayEnd, ImmutableList<String> expectedMessageParts)
        {
            this.location = new CanonicalSpan(start, end);
            this.displayLocation = TestUtil.fx(() -> new DisplaySpan(displayStart, displayEnd));
            this.expectedMessageParts = expectedMessageParts;
        }
    }
    
    @SuppressWarnings("units")
    static final Error e(int start, int end, String... errorMessagePart)
    {
        return new Error(start, end, ImmutableList.copyOf(errorMessagePart));
    }

    @SuppressWarnings("units")
    static final Error e(int start, int end, int displayStart, int displayEnd, String... errorMessagePart)
    {
        return new Error(start, end, displayStart, displayEnd, ImmutableList.copyOf(errorMessagePart));
    }
    
    // Also ignores case
    class MultiSubstringMatcher extends SubstringMatcher
    {
        private final ImmutableList<String> substrings;

        public MultiSubstringMatcher(ImmutableList<String> substrings)
        {
            super(substrings.stream().collect(Collectors.joining("\u2026")));
            this.substrings = substrings;
        }

        @Override
        protected boolean evalSubstringOf(String string)
        {
            int curIndex = 0;
            for (String sub : substrings)
            {
                curIndex = string.toLowerCase().indexOf(sub.toLowerCase(), curIndex);
                if (curIndex == -1)
                {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected String relationship()
        {
            return "contains string(s)";
        }
    }
}
