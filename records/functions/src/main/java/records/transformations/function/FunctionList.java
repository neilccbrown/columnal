package records.transformations.function;

import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.function.comparison.Max;
import records.transformations.function.comparison.MaxIndex;
import records.transformations.function.comparison.Min;
import records.transformations.function.comparison.MinIndex;
import records.transformations.function.conversion.ExtractNumber;
import records.transformations.function.core.AsType;
import records.transformations.function.core.AsUnit;
import records.transformations.function.core.TypeOf;
import records.transformations.function.datetime.AddDays;
import records.transformations.function.datetime.DaysBetween;
import records.transformations.function.datetime.SecondsBetween;
import records.transformations.function.list.*;
import records.transformations.function.lookup.LookupFunctions;
import records.transformations.function.math.Logarithm;
import records.transformations.function.math.LogarithmNatural;
import records.transformations.function.number.Round;
import records.transformations.function.number.RoundDP;
import records.transformations.function.number.RoundSF;
import records.transformations.function.text.StringJoin;
import records.transformations.function.text.StringJoinWith;
import utility.Utility;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Created by neil on 13/12/2016.
 */
public class FunctionList
{
    public static ImmutableList<FunctionDefinition> getAllFunctions(UnitManager unitManager) throws InternalException
    {
        return Utility.<FunctionDefinition>concatStreams(Arrays.<FunctionDefinition>asList(
            new Absolute(),
            new AddDays(),
            new AnyAllNone.Any(),
            new AnyAllNone.All(),
            new AnyAllNone.None(),
            new AsType(),
            new AsUnit(),
            new Combine(),
            new Count(),
            new CountWhere(),
            new DaysBetween(),
            new ExtractNumber(),
            new GetElement(),
            new GetElementOrDefault(),
            new InList(),
            new JoinLists(),
            new KeepFunction(),
            new Logarithm(),
            new LogarithmNatural(),
            new MapFunction(),
            new Max(),
            new MaxIndex(),
            new Mean(),
            new Min(),
            new MinIndex(),
            new Not(),
            new Round(),
            new RoundDP(),
            new RoundSF(),
            new SecondsBetween(),
            new Single(),
            new StringJoin(),
            new StringJoinWith(),
            // TODO document and put back all these string functions:
            //new StringLeft(),
            new StringLength(),
            //new StringMid(),
            new StringReplaceAll(),
            //new StringRight(),
            new StringTrim(),
            //new StringWithin(),
            //new StringWithinIndex(),
            new Sum(),
            new ToString(),
            new TypeOf(),
            new Xor()
        ).stream(),
            FromString.getFunctions().stream(),
            LookupFunctions.getLookupFunctions().stream(),
            Stream.<ToTemporalFunction>of(
                // TODO document and put back all these date conversion functions:
                new ToDate(),
                new ToTime(),
                new ToDateTime(),
                new ToDateTimeZone(),
                new ToYearMonth()
        ).<FunctionDefinition>flatMap(t -> {
            try
            {
                return t.getTemporalFunctions(unitManager).stream();
            }
            catch (InternalException e)
            {
                Log.log(e);
                return Stream.empty();
            }
        })).collect(ImmutableList.<FunctionDefinition>toImmutableList());
    }

    public static @Nullable FunctionDefinition lookup(UnitManager mgr, String functionName) throws InternalException
    {
        for (FunctionDefinition functionDefinition : getAllFunctions(mgr))
        {
            if (functionDefinition.getName().equals(functionName) || functionDefinition.getScopedName().equals(functionName))
                return functionDefinition;
        }
        return null;
    }

    public static FunctionLookup getFunctionLookup(UnitManager unitManager)
    {
        return new FunctionLookup()
        {
            @Override
            public @Nullable StandardFunctionDefinition lookup(String functionName) throws InternalException
            {
                return FunctionList.lookup(unitManager, functionName);
            }

            @Override
            public ImmutableList<StandardFunctionDefinition> getAllFunctions() throws InternalException
            {
                return Utility.mapListI(FunctionList.getAllFunctions(unitManager), f -> f);
            }
        };
    }
}
