package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager.TagInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.AndExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.TimesExpression;
import records.transformations.expression.VarDeclExpression;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExFunction;
import utility.ExSupplier;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BackwardsMatch extends BackwardsProvider
{
    private static class VarInfo
    {
        private final String name;
        private final DataType type;
        private final Object value;

        public VarInfo(String name, DataType type, Object value)
        {
            this.name = name;
            this.type = type;
            this.value = value;
        }
    }
    
    private int nextVar = 0;
    private ArrayList<ArrayList<VarInfo>> varContexts = new ArrayList<>();

    public BackwardsMatch(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }
    
    private @Nullable VarInfo findVarOfType(Predicate<DataType> typePred)
    {
        ArrayList<VarInfo> possibles = new ArrayList<>();
        for (ArrayList<VarInfo> varContext : varContexts)
        {
            for (VarInfo varInfo : varContext)
            {
                if (typePred.test(varInfo.type))
                {
                    possibles.add(varInfo); 
                }
            }
        }
        return possibles.isEmpty() ? null : r.choose(possibles);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        // For temporal, numerical, boolean and text, we manipulate them to be what we want.
        // For boolean, we may use an equals expression.
        
        return targetType.apply(new DataTypeVisitor<List<ExpressionMaker>>()
        {
            @Override
            public List<ExpressionMaker> number(NumberInfo numberInfo) throws InternalException, UserException
            {
                @Nullable VarInfo numVar = findVarOfType(DataType::isNumber);
                if (numVar == null)
                    return ImmutableList.of();
                @NonNull VarInfo numVarFinal = numVar;
                IdentExpression varRef = new IdentExpression(numVar.name);
                return ImmutableList.of(() -> {
                    return new AddSubtractExpression(ImmutableList.of(
                        new TimesExpression(ImmutableList.of(varRef, new NumericLiteral(1, parent.makeUnitExpression(numberInfo.getUnit().divideBy(numVarFinal.type.getNumberInfo().getUnit()))))),
                            new NumericLiteral(Utility.addSubtractNumbers((Number)targetValue, (Number)numVarFinal.value, false), parent.makeUnitExpression(numberInfo.getUnit()))
                    ), ImmutableList.of(Op.SUBTRACT));
                });
            }

            @Override
            public List<ExpressionMaker> text() throws InternalException, UserException
            {
                // TODO include text replace
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                // TODO include date manipulation
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> bool() throws InternalException, UserException
            {
                @Nullable VarInfo boolVar = findVarOfType(t -> t.equals(DataType.BOOLEAN));
                if (boolVar == null)
                    return ImmutableList.of();
                else
                {
                    String name = boolVar.name;
                    return ImmutableList.of(() -> new IdentExpression(name));
                }
            }

            @Override
            public List<ExpressionMaker> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public List<ExpressionMaker> array(DataType inner) throws InternalException, UserException
            {
                return ImmutableList.of();
            }
        });
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of(() -> makeMatch(maxLevels, targetType, targetValue));
    }

    /**
     * Make a match expression or an if expression with a match.
     * 
     * @return A MatchExpression that evaluates to the correct outcome.
     * @throws InternalException
     * @throws UserException
     */
    @OnThread(Tag.Simulation)
    private Expression makeMatch(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        DataType t = parent.makeType();
        @Value Object actual = parent.makeValue(t);
        // Make a bunch of guards which won't fire:
        List<Function<MatchExpression, MatchClause>> clauses = new ArrayList<>(TestUtil.makeList(r, 0, 4, (ExSupplier<Optional<Function<MatchExpression, MatchClause>>>)() -> {
            // Generate a bunch which can't match the item:
            List<ExFunction<MatchExpression, Pattern>> patterns = makeNonMatchingPatterns(maxLevels - 1, t, actual);
            Expression outcome = parent.make(targetType, parent.makeValue(targetType), maxLevels - 1);
            if (patterns.isEmpty())
                return Optional.<Function<MatchExpression, MatchClause>>empty();
            return Optional.<Function<MatchExpression, MatchClause>>of((MatchExpression me) -> {
                try
                {
                    return me.new MatchClause(Utility.<ExFunction<MatchExpression, Pattern>, Pattern>mapListEx(patterns, p -> p.apply(me)), outcome);
                }
                catch (UserException | InternalException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }).stream().<Function<MatchExpression, MatchClause>>flatMap(o -> o.isPresent() ? Stream.<Function<MatchExpression, MatchClause>>of(o.get()) : Stream.<Function<MatchExpression, MatchClause>>empty()).collect(Collectors.<Function<MatchExpression, MatchClause>>toList()));
        List<ExFunction<MatchExpression, Pattern>> patterns = new ArrayList<>(makeNonMatchingPatterns(maxLevels - 1, t, actual));
        
        // Add var context for successful pattern:
        varContexts.add(new ArrayList<>());
        Pair<Expression, @Nullable Expression> match = makePatternMatch(maxLevels - 1, t, actual);
        Expression correctOutcome = parent.make(targetType, targetValue, maxLevels - 1);
        patterns.add(r.nextInt(0, patterns.size()), me -> {
            @Nullable Expression guard = r.nextBoolean() ? null : parent.make(DataType.BOOLEAN, true, maxLevels - 1);
            @Nullable Expression extraGuard = match.getSecond();
            if (extraGuard != null)
                guard = (guard == null ? extraGuard : new AndExpression(Arrays.asList(guard, extraGuard)));
            return new Pattern(match.getFirst(), guard);
        });
        // Remove for successful pattern:
        varContexts.remove(varContexts.size() -1);
        clauses.add(r.nextInt(0, clauses.size()), me -> {
            try
            {
                return me.new MatchClause(Utility.<ExFunction<MatchExpression, Pattern>, Pattern>mapListEx(patterns, p -> p.apply(me)), correctOutcome);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        });
        return new MatchExpression(parent.make(t, actual, maxLevels - 1), clauses);
    }


    // Pattern and an optional guard
    @NonNull
    private Pair<Expression, @Nullable Expression> makePatternMatch(int maxLevels, DataType t, Object actual)
    {
        try
        {
            if (t.isTagged() && r.nextBoolean())
            {
                TaggedValue p = (TaggedValue) actual;
                return t.apply(new SpecificDataTypeVisitor<Pair<Expression, @Nullable Expression>>()
                {
                    @Override
                    @OnThread(value = Tag.Simulation, ignoreParent = true)
                    public Pair<Expression, @Nullable Expression> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes) throws InternalException, UserException
                    {
                        TagType<DataType> tagType = tagTypes.get(p.getTagIndex());
                        @Nullable DataType inner = tagType.getInner();
                        @Nullable TaggedTypeDefinition typeDefinition = DummyManager.INSTANCE.getTypeManager().lookupDefinition(typeName);
                        if (typeDefinition == null)
                            throw new InternalException("Looked up type but null definition: " + typeName);
                        if (inner == null)
                            return new Pair<>(TestUtil.tagged(Either.right(new TagInfo(typeDefinition, p.getTagIndex())), null), null);
                        @Nullable Object innerValue = p.getInner();
                        if (innerValue == null)
                            throw new InternalException("Type says inner value but is null");
                        Pair<Expression, @Nullable Expression> subPattern = makePatternMatch(maxLevels, inner, innerValue);
                        return new Pair<>(TestUtil.tagged(Either.right(new TagInfo(typeDefinition, p.getTagIndex())), subPattern.getFirst()), subPattern.getSecond());
                    }
                });

            }
            else if (r.nextBoolean()) // Do equals but using variable + guard
            {
                String varName = "var" + nextVar++;
                if (!varContexts.isEmpty())
                    varContexts.get(varContexts.size() - 1).add(new VarInfo(varName, t, actual));
                return new Pair<>(new VarDeclExpression(varName), new EqualExpression(ImmutableList.of(new IdentExpression(varName), parent.make(t, actual, maxLevels))));
            }
            Expression expression = parent.make(t, actual, maxLevels);
            return new Pair<Expression, @Nullable Expression>(expression, null);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Simulation)
    private List<ExFunction<MatchExpression, Pattern>> makeNonMatchingPatterns(final int maxLevels, final DataType t, @Value Object actual)
    {
        class CantMakeNonMatching extends RuntimeException {}
        try
        {
            return TestUtil.<ExFunction<MatchExpression, Pattern>>makeList(r, 1, 4, () ->
            {
                Pair<Expression, @Nullable Expression> match = r.choose(Arrays.<ExSupplier<Pair<Expression, @Nullable Expression>>>asList(
                        () ->
                        {
                            @Value Object nonMatchingValue;
                            int attempts = 0;
                            do
                            {
                                nonMatchingValue = parent.makeValue(t);
                                if (attempts++ >= 30)
                                    throw new CantMakeNonMatching();
                            }
                            while (Utility.compareValues(nonMatchingValue, actual) == 0);
                            Object nonMatchingValueFinal = nonMatchingValue;
                            return makePatternMatch(maxLevels - 1, t, nonMatchingValueFinal);
                        }
                )).get();
                @Nullable Expression guard = r.nextBoolean() ? null : parent.make(DataType.BOOLEAN, true, maxLevels - 1);
                @Nullable Expression extraGuard = match.getSecond();
                if (extraGuard != null)
                    guard = (guard == null ? extraGuard : new AndExpression(Arrays.asList(guard, extraGuard)));
                @Nullable Expression guardFinal = guard;
                return (ExFunction<MatchExpression, Pattern>)((MatchExpression me) -> new Pattern(match.getFirst(), guardFinal));
            });
        }
        catch (CantMakeNonMatching e)
        {
            return Collections.emptyList();
        }
    }

}
