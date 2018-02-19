package test.gui;

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.TableManager;
import records.data.datatype.NumberDisplayInfo;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.OperandOps;
import records.transformations.Transform;
import records.transformations.TransformationInfo;
import records.transformations.expression.Expression;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestQuickFix extends ApplicationTest implements EnterExpressionTrait, ScrollToTrait, ComboUtilTrait, ListUtilTrait
{
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
    }
    
    // Test that adding two strings suggests a quick fix to switch to string concatenation
    @Test
    @OnThread(Tag.Simulation)
    public void testStringAdditionFix1()
    {
        testFix("\"A\"+\"B\"", "A", "", "\"A\" ; \"B\"");
    }
    
    @Test
    @OnThread(Tag.Simulation)
    public void testStringAdditionFix2()
    {
        testFix("\"A\"+S1+\"C\"", "C", "", "\"A\" ; @column S1 ; \"C\"");
    }
    
    @Test
    public void testUnitLiteralFix1()
    {
        testFix("ACC1+6", "6", "", "@column ACC1 + 6{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix1B()
    {
        testFix("6-ACC1", "6", "", "6{m/s^2} - @column ACC1");
    }

    @Test
    public void testUnitLiteralFix2()
    {
        testFix("ACC1>6>ACC3", "6", "", "@column ACC1 > 6{m/s^2} > @column ACC3");
    }

    @Test
    public void testUnitLiteralFix3()
    {
        testFix("ACC1<>103", "103", "", "@column ACC1 <> 103{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix4()
    {
        testFix("@ifACC1=ACC2=32@then2@else7+6", "32", "", "@if (@column ACC1 = @column ACC2 = 32{m/s^2}) @then 2 @else (7 + 6)");
    }

    @Test
    public void testUnitLiteralFix5()
    {
        testFix("@matchACC1@case3@then5", "3", "", "@match @column ACC1 @case 3{m/s^2} @then 5");
    }

    @Test
    public void testUnitLiteralFix6()
    {
        testFix("@matchACC1@case3@then52@case12@or14@then63", "3", "", "@match @column ACC1 @case 3{m/s^2} @then 52 @case 12 @or 14 @then 63");
    }

    @Test
    public void testUnitLiteralFix6B()
    {
        testFix("@matchACC1@case3@then52@case12@or14@then63", "12", "", "@match @column ACC1 @case 3 @then 52 @case 12{m/s^2} @or 14 @then 63");
    }

    @Test
    public void testUnitLiteralFix6C()
    {
        testFix("@matchACC1@case3@then52@case12@or14@then63", "14", "", "@match @column ACC1 @case 3 @then 52 @case 12 @or 14{m/s^2} @then 63");
    }

    @Test
    public void testBracketFix1()
    {
        testSimpleFix("1+2*3", "*","1 + (2 * 3)");
    }

    @Test
    public void testBracketFix1B()
    {
        testSimpleFix("1+2*3", "+", "(1 + 2) * 3");
    }

    @Test
    public void testBracketFix2()
    {
        testSimpleFix("1 + 2 = 3", "+", "(1 + 2) = 3");
    }

    @Test
    public void testBracketFix3()
    {
        testSimpleFix("1 + 2 = 3 - 4", "-", "@invalidops 1 \"+\" 2 \"=\" (3 - 4)");
    }
    
    @Test
    public void testBracketFix4()
    {
        testSimpleFix("1 = 2 = 3 + 4 = 5 = 6", "+", "1 = 2 = (3 + 4) = 5 = 6");
    }

    @Test
    public void testBracketFix5()
    {
        // Tuples must be bracketed:
        testSimpleFix("1 , 2", ",", "(1, 2)");
    }
    
    @Test
    public void testBracketFix5B()
    {
        testSimpleFix("1 , 2", ",", "[1, 2]");
    }

    @Test
    public void testBracketFix6() throws UserException, InternalException
    {
        testFix("1 + 2 + (3 * 4 / 5) + 6", "*", dotCssClassFor("(3 * 4) / 5"), "1 + 2 + ((3 * 4) / 5) + 6");
    }

    @Test
    public void testBracketFix6B() throws UserException, InternalException
    {
        testFix("1 + 2 + (3 * 4 / 5) + 6", "*", dotCssClassFor("3 * (4 / 5)"), "1 + 2 + (3 * (4 / 5)) + 6");
    }

    @Test
    public void testBracketFix7() throws UserException, InternalException
    {
        // Test that inner square brackets are preserved:
        testFix("1 + 2 + [3 * 4 / 5] + 6", "*", dotCssClassFor("3 * (4 / 5)"), "1 + 2 + [3 * (4 / 5)] + 6");
    }

    @Test
    public void testBracketFix8() throws UserException, InternalException
    {
        // Test that inner square brackets are preserved:
        testFix("@if true @then abs(-5 - -6 * -7) @else 8", "*", dotCssClassFor("-5 - (-6 * -7)"), "@if true @then abs(-5 - (-6 * -7)) @else 8");
    }
    
    @Test
    public void testListBracketFix1()
    {
        // If a function takes a list, and the user passes either one item (which is not of list type)
        // or a tuple, offer to switch to list brackets:
        testFix("sum(2)", "sum", "", "sum([2])");
    }

    @Test
    public void testListBracketFix2() throws UserException, InternalException
    {
        // If a function takes a list, and the user passes either one item (which is not of list type)
        // or a tuple, offer to switch to list brackets:
        testFix("sum(2, 3, 4)", "sum", dotCssClassFor("sum([2, 3, 4])"), "sum([2, 3, 4])");
    }
    
    @Test
    public void testColumnToListFix1() throws UserException, InternalException
    {
        // If a column-single-row is used where a list is expected, offer to switch to
        // a whole-column item:
        testSimpleFix("sum(ACC1)", "sum", "sum(@wholecolumn ACC1)");
    }

    @Test
    public void testColumnFromListFix1() throws UserException, InternalException
    {
        // If a column-all-rows is used where a non-list is expected, offer to switch to
        // a column-single-row item:
        
        // Note units aren't right here, but fix should still be offered:
        testFix("ACC1§ + 6", "ACC1", dotCssClassFor("@column ACC1"), "@column ACC1 + 6");
    }
    
    
    
    private void testSimpleFix(String original, String fixFieldContent, String fixed)
    {
        try
        {
            testFix(original, fixFieldContent, dotCssClassFor(fixed), fixed);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private String dotCssClassFor(String expression) throws InternalException, UserException
    {
        return "." + OperandOps.makeCssClass(Expression.parse(null, expression, DummyManager.INSTANCE.getTypeManager()));
    }

    /**
     * 
     * @param original Original expression
     * @param fixFieldContent Content of the field to focus on when looking for fix
     * @param fixId The CSS selector to use to look for the particular fix row
     * @param result The expected outcome expression after applying the fix
     */
    private void testFix(String original, String fixFieldContent, String fixId, String result)
    {
        try
        {
            UnitManager u = new UnitManager();
            TypeManager typeManager = new TypeManager(u);
            List<ExFunction<RecordSet, ? extends EditableColumn>> columns = new ArrayList<>();
            for (int i = 1; i <= 3; i++)
            {
                int iFinal = i;
                columns.add(rs -> new MemoryStringColumn(rs, new ColumnId("S" + iFinal), Collections.emptyList(), ""));
                columns.add(rs -> new MemoryNumericColumn(rs, new ColumnId("ACC" + iFinal), new NumberInfo(u.loadUse("m/s^2")), Collections.emptyList(), 0));
            }
            @SuppressWarnings("keyfor")
            TableManager tableManager = TestUtil.openDataAsTable(windowToUse, typeManager, new EditableRecordSet(columns, () -> 0));

            scrollTo(".id-tableDisplay-menu-button");
            clickOn(".id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-addTransformation");
            selectGivenListViewItem(lookup(".transformation-list").query(), (TransformationInfo ti) -> ti.getDisplayName().toLowerCase().startsWith("calculate"));
            push(KeyCode.TAB);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            push(KeyCode.TAB);
            write(original);
            Node lhs = lookup(".entry-field").<Node>match((Predicate<Node>) (n -> TestUtil.fx(() -> ((TextField) n).getText().equals(fixFieldContent)))).<Node>query();
            assertNotNull(lhs);
            if (lhs == null) return;
            @NonNull Node lhsFinal = lhs;
            if (!TestUtil.fx(() -> lhsFinal.isFocused()))
            {
                // Get rid of any popups in the way:
                push(KeyCode.ESCAPE);
                push(KeyCode.ESCAPE);
                push(KeyCode.ESCAPE);
                clickOn(lhs);
                TestUtil.sleep(2000);
            }
            @Nullable Window errorPopup = listWindows().stream().filter(w -> w instanceof PopOver).findFirst().orElse(null);
            assertNotNull(errorPopup);
            assertEquals(lookup(".expression-info-error").queryAll().stream().map(n -> textFlowToString(n)).collect(Collectors.joining(" /// ")),
                1L, lookup(".expression-info-error").queryAll().stream().filter(Node::isVisible).count());
            assertEquals("Looking for row that matches, among: " + lookup(".quick-fix-row").<Node>queryAll().stream().flatMap(n -> TestUtil.fx(() -> n.getStyleClass()).stream()).collect(Collectors.joining(", ")), 
                1, lookup(".quick-fix-row" + fixId).queryAll().size());
            // Get around issue with not being able to get the position of
            // items in the fix popup correctly, by using keyboard:
            //moveTo(".quick-fix-row" + fixId);
            //clickOn(".quick-fix-row" + fixId);
            Node fixRow = lookup(".quick-fix-row" + fixId).queryAll().iterator().next();
            List<String> fixStyles = TestUtil.fx(() -> fixRow.getStyleClass());
            String key = fixStyles.stream().filter(c -> c.startsWith("key-")).map(c -> c.substring("key-".length())).findFirst().orElse("");
            assertNotEquals(Utility.listToString(fixStyles), "", key);
            push(KeyCode.SHIFT, KeyCode.valueOf(key));
            // Check that popup vanishes pretty much straight away:
            TestUtil.sleep(200);
            assertTrue("Popup still showing: "+ errorPopup, TestUtil.fx(() -> errorPopup != null && !errorPopup.isShowing()));
            WaitForAsyncUtils.waitForFxEvents();
            moveTo(".ok-button");
            TestUtil.sleep(3000);
            clickOn(".ok-button");
            TestUtil.sleep(1000);
            WaitForAsyncUtils.waitForFxEvents();
            @Nullable Transform transform = Utility.filterClass(tableManager.getAllTables().stream(), Transform.class).findFirst().orElse(null);
            assertNotNull(transform);
            if (transform == null)
                return;
            assertEquals(1, transform.getCalculatedColumns().size());
            assertEquals(result, transform.getCalculatedColumns().get(0).getSecond().toString());
        }
        catch (Exception e)
        {
            // Test fails regardless, so no harm turning checked exception
            // into unchecked for simpler signatures:
            throw new RuntimeException(e);
        }
    }

    private String textFlowToString(Node n)
    {
        return TestUtil.fx(() -> n.toString() + " " + n.localToScreen(n.getBoundsInLocal().getMinX(), n.getBoundsInLocal().getMinY()) + ((TextFlow)n).getChildren().stream().map(c -> ((Text)c).getText()).collect(Collectors.joining(";")));
    }
}
