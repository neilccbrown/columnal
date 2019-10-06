package test.expressions;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.lexeditor.ExpressionEditor;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.Expression.SaveDestination;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.visitor.ExpressionVisitorStream;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenNonsenseExpression;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by neil on 30/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveExpression extends FXApplicationTest
{
    @Property(trials = 200)
    public void testLoadSaveNonsense(@From(GenNonsenseExpression.class) Expression expression) throws InternalException, UserException
    {
        testLoadSave(expression);
    }

    @Property(trials = 200)
    public void testEditNonsense(@From(GenNonsenseExpression.class) Expression expression) throws InternalException, UserException
    {
        TestUtil.fxTest_(() -> {
            testNoOpEdit(expression);
        });
    }
    
    @Test
    @OnThread(Tag.FXPlatform)
    public void testUnit() throws InternalException, UserException
    {
        TestUtil.fxTest_(() -> {
            try
            {
                testNoOpEdit("@ifN100m Time=~@call tag\\\\Optional\\Is(m)@then100{m}/m@else@ifN100yd time=~@call tag\\\\Optional\\Is(y)@then@call function\\\\convert unit(unit{m/s},100{yard}/y)@else0{m/s}@endif@endif");
            }
            catch (UserException | InternalException e)
            {
                throw new RuntimeException(e);
            }
        });
    }
    
    @Test
    public void testInvalids()
    {
        TestUtil.fxTest_(() -> {
            try
            {
                testNoOpEdit("@invalidops(2, @unfinished \"+\")");
                testNoOpEdit("@invalidops(2, @unfinished \"%\", 3)");
                testNoOpEdit("@invalidops(1, @unfinished \"+\", 2, @unfinished \"*\", 3)");
                testNoOpEdit("@invalidops(1, @unfinished \"+\", -2, @unfinished \"*\", 3)");
                testNoOpEdit("@invalidops(-1, @unfinished \"+\", -2, @unfinished \"*\", 3)");
                testNoOpEdit("-1");
            }
            catch (UserException | InternalException e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    @OnThread(Tag.FXPlatform)
    public void testNoOpEdit(String src) throws UserException, InternalException
    {
        TypeManager typeManager = DummyManager.make().getTypeManager();
        testNoOpEdit(TestUtil.parseExpression(src, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager())));
    }

    @OnThread(Tag.FXPlatform)
    private void testNoOpEdit(Expression expression)
    {
        TypeManager typeManager = DummyManager.make().getTypeManager();
        // To preserve column references we have to have them in the lookup:
        ColumnLookup columnLookup = new ColumnLookup()
        {
            @Override
            public @Nullable FoundColumn getColumn(ColumnReference columnReference)
            {
                return null;
            }

            @Override
            public @Nullable FoundTable getTable(@Nullable TableId tableName) throws UserException, InternalException
            {
                return null;
            }

            @Override
            public Stream<ColumnReference> getAvailableColumnReferences()
            {
                return expression.allColumnReferences().distinct();
            }

            @Override
            public Stream<TableId> getAvailableTableReferences()
            {
                return expression.visit(new ExpressionVisitorStream<TableId>() {
                    @Override
                    public Stream<TableId> ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
                    {
                        if (Objects.equals(namespace, "table"))
                        {
                            return Stream.of(new TableId(idents.get(0)));
                        }
                        return super.ident(self, namespace, idents, isVariable);
                    }
                }).distinct();
            }

            @Override
            public Stream<ClickedReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId)
            {
                // Not used:
                return Stream.empty();
            }
        };
        
        
        Expression edited = new ExpressionEditor(expression, new ReadOnlyObjectWrapper<@Nullable Table>(null), new ReadOnlyObjectWrapper<>(columnLookup), null, null, typeManager, () -> TestUtil.createTypeState(typeManager), FunctionList.getFunctionLookup(typeManager.getUnitManager()), e -> {
        }).save(false);
        assertEquals(expression, edited);
        assertEquals(expression.save(SaveDestination.SAVE_EXTERNAL, BracketedStatus.NEED_BRACKETS, typeManager, TableAndColumnRenames.EMPTY), edited.save(SaveDestination.SAVE_EXTERNAL, BracketedStatus.NEED_BRACKETS, typeManager, TableAndColumnRenames.EMPTY));
        assertEquals(expression.save(SaveDestination.SAVE_EXTERNAL, BracketedStatus.DONT_NEED_BRACKETS, typeManager, TableAndColumnRenames.EMPTY), edited.save(SaveDestination.SAVE_EXTERNAL, BracketedStatus.DONT_NEED_BRACKETS, typeManager, TableAndColumnRenames.EMPTY));
    }

    @Property(trials = 200)
    public void testLoadSaveReal(@From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue) throws InternalException, UserException
    {
        try
        {
            testLoadSave(expressionValue.expression);
        }
        catch (OutOfMemoryError e)
        {
            fail("Out of memory issue with expression: " + expressionValue.expression);
        }
    }

    private void testLoadSave(@From(GenNonsenseExpression.class) Expression expression) throws UserException, InternalException
    {
        String saved = expression.save(SaveDestination.SAVE_EXTERNAL, BracketedStatus.NEED_BRACKETS, null, TableAndColumnRenames.EMPTY);
        // Use same manager to load so that types are preserved:
        TypeManager typeManager = TestUtil.managerWithTestTypes().getFirst().getTypeManager();
        Expression reloaded = TestUtil.parseExpression(saved, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
        assertEquals("Saved version: " + saved, expression, reloaded);
        String resaved = reloaded.save(SaveDestination.SAVE_EXTERNAL, BracketedStatus.NEED_BRACKETS, null, TableAndColumnRenames.EMPTY);
        assertEquals(saved, resaved);

    }
}
