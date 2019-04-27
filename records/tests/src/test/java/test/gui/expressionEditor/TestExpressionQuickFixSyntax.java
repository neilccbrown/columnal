package test.gui.expressionEditor;

import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.service.query.NodeQuery;
import org.testfx.util.WaitForAsyncUtils;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.Table.InitialLoadDetails;
import records.data.TableManager;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow.MainWindowActions;
import records.transformations.expression.ExpressionUtil;
import records.gui.grid.RectangleBounds;
import records.gui.lexeditor.EditorDisplay;
import records.transformations.Calculate;
import records.transformations.expression.Expression;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ComboUtilTrait;
import test.gui.trait.EnterExpressionTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionQuickFixSyntax extends BaseTestQuickFix
{
    // Test that adding two strings suggests a quick fix to switch to string concatenation
    @Test
    @OnThread(Tag.Simulation)
    public void testStringAdditionFix1()
    {
        testFix("\"A\"+\"B\"", "A", "", "\"A\";\"B\"");
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
        testFix("ACC1+6{1}", "6", "", "@column ACC1 + 6{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix1B()
    {
        testFix("6{1}-ACC1", "6", "", "6{m/s^2} - @column ACC1");
    }

    @Test
    public void testUnitLiteralFix2()
    {
        testFix("ACC1>6{1}>ACC3", "6", "", "@column ACC1 > 6{m/s^2} > @column ACC3");
    }

    @Test
    public void testUnitLiteralFix3()
    {
        testFix("ACC1<>103{m/s}", "103", "", "@column ACC1 <> 103{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix3B()
    {
        testFix("ACC1=103{1}", "103", "", "@column ACC1 = 103{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix4()
    {
        testFix("@ifACC1=ACC2=32{1}@then2@else7+6@endif", "32", "", "@if (@column ACC1 = @column ACC2 = 32{m/s^2}) @then 2 @else (7 + 6) @endif");
    }

    @Test
    public void testUnitLiteralFix5()
    {
        testFix("@matchACC1@case3{1}@then5@endmatch", "3", "", "@match @column ACC1 @case 3{m/s^2} @then 5 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6()
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "3{1}", "", "@match @column ACC1 @case 3{m/s^2} @then 52 @case 12{1} @orcase 14{1} @then 63 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6B()
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "12", "", "@match @column ACC1 @case 3{1} @then 52 @case 12{m/s^2} @orcase 14{1} @then 63 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6C()
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "14", "", "@match @column ACC1 @case 3{1} @then 52 @case 12{1} @orcase 14{m/s^2} @then 63 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6D()
    {
        testFix("@matchACC1@case12{1}@orcase14{1}@then63@endmatch", "14", "", "@match @column ACC1 @case 12{1} @orcase 14{m/s^2} @then 63 @endmatch");
    }

    @Test
    public void testUnitLiteralFix7() throws UserException, InternalException
    {
        testFix("12{metre/s}", "metre", dotCssClassFor("m"), "12{m/s}");
    }

    @Test
    public void testUnitLiteralFix8() throws UserException, InternalException
    {
        testFix("type{Number{metre/s}}", "metre", dotCssClassFor("m"), "type{Number{m/s}}");
    }

    @Test
    public void testBracketFix1()
    {
        testSimpleFix("1 + 2 * 3", "*","1 + (2 * 3)");
    }

    @Test
    public void testBracketFix1B()
    {
        testSimpleFix("1+ 2*3", "+", "(1 + 2) * 3");
    }

    @Test
    public void testBracketFix2()
    {
        testSimpleFix("1 + 2 = 3", "+", "(1 + 2) = 3");
    }

    @Test
    public void testBracketFix3()
    {
        testSimpleFix("1 + 2 = 3 - 4", "-", "@invalidops(1, @unfinished \"+\", 2, @unfinished \"=\", (3 - 4))");
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
        testSimpleFix("1, 2", ",", "(1, 2)");
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
        // Test minuses:
        testFix("@if true @then abs(-5 - -6 * -7) @else 8 @endif", "*", dotCssClassFor("-5 - (-6 * -7)"), "@if true @then @call @function abs(-5 - (-6 * -7)) @else 8 @endif");
    }
    
    @Test
    @Ignore // Not sure this is a fix worth suggesting when there is only one argument
    public void testListBracketFix1()
    {
        // If a function takes a list, and the user passes either one item (which is not of list type)
        // or a tuple, offer to switch to list brackets:
        testFix("sum(2)", "2", "", "@call @function sum([2])");
    }

    @Test
    public void testListBracketFix2() throws UserException, InternalException
    {
        // If a function takes a list, and the user passes either one item (which is not of list type)
        // or a tuple, offer to switch to list brackets:
        testFix("sum(2, 3, 4)", "2", dotCssClassFor("@call @function sum([2, 3, 4])"), "@call @function sum([2, 3, 4])");
    }

    @Test
    public void testListBracketFix3() throws UserException, InternalException
    {
        // If user tries to use array index syntax, offer the element function instead:
        testFix("@entire ACC1[3]+1", "3", dotCssClassFor("@call @function element(@entire ACC1, 3)"), "@call @function element(@entire ACC1,3)+1");
    }
    
    @Test
    public void testColumnToListFix1() throws UserException, InternalException
    {
        // If a column-single-row is used where a list is expected, offer to switch to
        // a whole-column item:
        testSimpleFix("sum(ACC1)", "ACC1", "@call @function sum(@entire ACC1)");
    }
    
    @Ignore // Not sure if this fix is even worth implementing
    @Test
    public void testColumnFromListFix1() throws UserException, InternalException
    {
        // If a column-all-rows is used where a non-list is expected, offer to switch to
        // a column-single-row item:
        
        // Note units aren't right here, but fix should still be offered:
        testFix("@entire ACC1 + 6", "ACC1", dotCssClassFor("@column ACC1"), "@column ACC1 + 6");
    }

    @Test
    public void testUnmatchedBracketFix1()
    {
        testFix("(1+2", "", "." + ExpressionUtil.makeCssClass(")"), "(1 + 2)");
    }

    @Test
    public void testUnmatchedBracketFix2()
    {
        testFix("[1+2", "", "." + ExpressionUtil.makeCssClass("]"), "[1 + 2]");
    }

    @Test
    public void testUnmatchedBracketFix3()
    {
        testFix("([0];[1)", "", "." + ExpressionUtil.makeCssClass("]"), "([0] ; [1])");
    }

    @Test
    public void testUnmatchedIfFix1()
    {
        testFix("@iftrue@then0@else1", "", "." + ExpressionUtil.makeCssClass("@endif"), "@if true @then 0 @else 1 @endif");
    }

    @Test
    public void testUnmatchedIfFix2()
    {
        testFix("@iftrue", "", "." + ExpressionUtil.makeCssClass("@then@else@endif"), "@if true @then @invalidops () @else @invalidops () @endif");
    }

    @Test
    public void testUnmatchedIfFix3()
    {
        testFix("@iftrue@endif", "", "." + ExpressionUtil.makeCssClass("@then@else"), "@if true @then @invalidops () @else @invalidops () @endif");
    }

    @Test
    public void testUnmatchedIfFix4()
    {
        testFix("@if1@else", "1", "." + ExpressionUtil.makeCssClass("@then"), "@invalidops(@unfinished \"^aif\", 1, @unfinished \"^athen\", @invalidops (), @unfinished \"^aelse\", @invalidops ())");
    }
    
}