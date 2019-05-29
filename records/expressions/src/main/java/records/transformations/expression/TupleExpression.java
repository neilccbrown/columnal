package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TupleTypeExp;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Pair;
import utility.Utility;
import utility.Utility.TransparentBuilder;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by neil on 12/01/2017.
 */
public class TupleExpression extends Expression
{
    private final ImmutableList<@Recorded Expression> members;
    private @Nullable ImmutableList<TypeExp> memberTypes;
    private @Nullable TypeExp tupleType;

    public TupleExpression(ImmutableList<@Recorded Expression> members)
    {
        this.members = members;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState state, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @NonNull TypeExp[] typeArray = new TypeExp[members.size()];
        ExpressionKind kind = ExpressionKind.EXPRESSION;
        for (int i = 0; i < typeArray.length; i++)
        {
            @Nullable CheckedExp c = members.get(i).check(dataLookup, state, LocationInfo.UNIT_DEFAULT, onError);
            if (c == null)
                return null;
            typeArray[i] = c.typeExp;
            state = c.typeState;
            kind = kind.or(c.expressionKind);
            
        }
        memberTypes = ImmutableList.copyOf(typeArray);
        tupleType = new TupleTypeExp(this, memberTypes, true);
        return onError.recordType(this, kind, state, tupleType);
    }
    
    @Override
    public ValueResult matchAsPattern(@Value Object value, final EvaluateState state) throws InternalException, UserException
    {
        if (value instanceof Object[])
        {
            @Value Object @Value[] tuple = (@Value Object @Value[]) value;
            if (tuple.length != members.size())
                throw new InternalException("Mismatch in tuple size, type is " + members.size() + " but found " + tuple.length);
            EvaluateState curState = state;
            TransparentBuilder<ValueResult> memberValues = new TransparentBuilder<>(tuple.length);
            for (int i = 0; i < tuple.length; i++)
            {
                ValueResult latest = memberValues.add(members.get(i).matchAsPattern(tuple[i], curState));
                if (Utility.cast(latest.value, Boolean.class) == false)
                    return explanation(DataTypeUtility.value(false), ExecutionType.MATCH, state, memberValues.build(), ImmutableList.of(), true);
                curState = latest.evaluateState;
            }
            return explanation(DataTypeUtility.value(true), ExecutionType.MATCH, curState, memberValues.build(), ImmutableList.of(), true);
        }
        throw new InternalException("Expected tuple but found " + value.getClass());
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        TransparentBuilder<ValueResult> valueResults = new TransparentBuilder<>(members.size());
        @Value Object[] values = new Object[members.size()];
        for (int i = 0; i < values.length; i++)
        {
            values[i] = valueResults.add(members.get(i).calculateValue(state)).value;
        }
        return result(DataTypeUtility.value(values), state, valueResults.build(), ImmutableList.of(), true);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String content = members.stream().map(e -> e.save(structured, BracketedStatus.DONT_NEED_BRACKETS, renames)).collect(Collectors.joining(", "));
        return "(" + content + ")";
    }
    
    @Override
    public StyledString toDisplay(BracketedStatus surround, ExpressionStyler expressionStyler)
    {
        StyledString content = members.stream().map(e -> e.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler)).collect(StyledString.joining(", "));
        return expressionStyler.styleExpression(StyledString.roundBracket(content), this);
    }

    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return IntStream.range(0, members.size()).mapToObj(i ->
            members.get(i)._test_allMutationPoints().map(p -> p.<Function<Expression, Expression>>replaceSecond(newExp -> new TupleExpression(Utility.replaceList(members, i, p.getSecond().apply(newExp)))))).flatMap(s -> s);
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

        TupleExpression that = (TupleExpression) o;

        return members.equals(that.members);
    }

    @Override
    public int hashCode()
    {
        return members.hashCode();
    }

    public ImmutableList<@Recorded Expression> getMembers()
    {
        return members;
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new TupleExpression(Utility.mapListI(members, e -> e.replaceSubExpression(toReplace, replaceWith)));
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.tuple(this, members);
    }
}
