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
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.typeExp.TupleTypeExp;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Pair;
import utility.StreamTreeBuilder;
import utility.Utility;

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
    public @Nullable CheckedExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @NonNull TypeExp[] typeArray = new TypeExp[members.size()];
        ExpressionKind kind = ExpressionKind.EXPRESSION;
        for (int i = 0; i < typeArray.length; i++)
        {
            @Nullable CheckedExp c = members.get(i).check(dataLookup, state, onError);
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
    public @Nullable EvaluateState matchAsPattern(@Value Object value, final EvaluateState state) throws InternalException, UserException
    {
        if (value instanceof Object[])
        {
            @Value Object @Value[] tuple = (@Value Object @Value[]) value;
            if (tuple.length != members.size())
                throw new InternalException("Mismatch in tuple size, type is " + members.size() + " but found " + tuple.length);
            @Nullable EvaluateState curState = state;
            for (int i = 0; i < tuple.length; i++)
            {
                curState = members.get(i).matchAsPattern(tuple[i], curState);
                if (curState == null)
                    return null;
            }
            return curState;
        }
        throw new InternalException("Expected tuple but found " + value.getClass());
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        @Value Object[] values = new Object[members.size()];
        for (int i = 0; i < values.length; i++)
        {
            Pair<@Value Object, EvaluateState> pair = members.get(i).getValue(state);
            values[i] = pair.getFirst();
            state = pair.getSecond();
        }
        return new Pair<>(DataTypeUtility.value(values), state);
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return members.stream().flatMap(Expression::allColumnReferences);
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        String content = members.stream().map(e -> e.save(BracketedStatus.MISC, renames)).collect(Collectors.joining(", "));
        if (surround == BracketedStatus.DIRECT_ROUND_BRACKETED)
            return content;
        else
            return "(" + content + ")";
    }
    
    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        StyledString content = members.stream().map(e -> e.toDisplay(BracketedStatus.MISC)).collect(StyledString.joining(", "));
        if (surround == BracketedStatus.DIRECT_ROUND_BRACKETED)
            return content;
        else
            return StyledString.roundBracket(content);
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionNodeParent>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<Expression, ExpressionNodeParent>> r = new StreamTreeBuilder();
        r.add(GeneralExpressionEntry.load(Keyword.OPEN_ROUND));
        for (int i = 0; i < members.size(); i++)
        {
            Expression item = members.get(i);
            r.addAll(item.loadAsConsecutive(BracketedStatus.MISC));
            if (i > 0)
                r.add(GeneralExpressionEntry.load(Keyword.COMMA));
        }
        r.add(GeneralExpressionEntry.load(Keyword.CLOSE_ROUND));
        return r.stream();
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
}
