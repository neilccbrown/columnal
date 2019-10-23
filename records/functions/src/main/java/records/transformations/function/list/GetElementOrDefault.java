package records.transformations.function.list;

import annotation.qual.Value;
import annotation.userindex.qual.UserIndex;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.explanation.ExplanationLocation;
import records.transformations.expression.function.ValueFunction;
import records.transformations.function.FunctionDefinition;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;

/**
 * Created by neil on 17/01/2017.
 */
public class GetElementOrDefault extends FunctionDefinition
{
    // Takes parameters: column/array, index, default
    public GetElementOrDefault() throws InternalException
    {
        super("list:element or");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            @Value int oneBasedIndex = intArg(1);
            @Value Object def = arg(2);
            @UserIndex int userIndex = DataTypeUtility.userIndex(oneBasedIndex);
            @Value ListEx list = arg(0, ListEx.class);
            if (userIndex < 1 || userIndex > list.size())
                return def;
            else
            {
                addUsedLocations(locs -> {
                    ExplanationLocation resultLoc = locs.get(0).getListElementLocation(oneBasedIndex - 1);
                    if (resultLoc != null)
                        setResultIsLocation(resultLoc);
                    return Utility.streamNullable(resultLoc);
                });
                return Utility.getAtIndex(list, userIndex);
            }
        }
    }
}
