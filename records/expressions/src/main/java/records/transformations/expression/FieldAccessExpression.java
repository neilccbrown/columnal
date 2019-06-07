package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Utility.Record;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

// Not a BinaryOpExpression because the field name by itself does not have a valid type.
public class FieldAccessExpression extends Expression
{
    private final @Recorded Expression lhsRecord;
    private final @Recorded Expression fieldName;

    public FieldAccessExpression(@Recorded Expression lhsRecord, @Recorded Expression fieldName)
    {
        this.lhsRecord = lhsRecord;
        this.fieldName = fieldName;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        Pair<@Nullable UnaryOperator<@Recorded TypeExp>, TypeState> lambda = ImplicitLambdaArg.detectImplicitLambda(this, ImmutableList.of(lhsRecord), typeState, onError);
        typeState = lambda.getSecond();
        
        CheckedExp lhsChecked = lhsRecord.check(dataLookup, typeState, ExpressionKind.EXPRESSION, locationInfo, onError);
        if (lhsChecked == null)
            return null;
        
        if (!(fieldName instanceof IdentExpression))
        {
            onError.recordError(fieldName, StyledString.s("Field name must be a valid name (and not an expression)"));
            return null;
        }
        
        @Recorded TypeExp fieldType = onError.recordTypeNN(this, new MutVar(this));
        TypeExp recordType = TypeExp.record(this, ImmutableMap.of(((IdentExpression)fieldName).getText(), fieldType), false);
        
        if (onError.recordError(this, TypeExp.unifyTypes(recordType, lhsChecked.typeExp)) == null)
            return null;
        
        return new CheckedExp(fieldType, typeState).applyToType(lambda.getFirst());
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        ValueResult lhsResult = lhsRecord.calculateValue(state);
        @Value Record record = Utility.cast(lhsResult.value, Record.class);
        
        return result(record.getField(((IdentExpression)fieldName).getText()), state, ImmutableList.of(lhsResult));
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.field(this, lhsRecord, fieldName);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String content = lhsRecord.save(structured, BracketedStatus.NEED_BRACKETS, renames) + "#" + fieldName.save(structured, BracketedStatus.NEED_BRACKETS, renames);
        if (surround == BracketedStatus.NEED_BRACKETS)
            return "(" + content + ")";
        else
            return content;
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.of();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldAccessExpression that = (FieldAccessExpression) o;
        return lhsRecord.equals(that.lhsRecord) &&
            fieldName.equals(that.fieldName);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(lhsRecord, fieldName);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(lhsRecord.toDisplay(BracketedStatus.NEED_BRACKETS, expressionStyler), StyledString.s("#" + fieldName)),this);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new FieldAccessExpression(lhsRecord.replaceSubExpression(toReplace, replaceWith), fieldName.replaceSubExpression(toReplace, replaceWith));
    }
}