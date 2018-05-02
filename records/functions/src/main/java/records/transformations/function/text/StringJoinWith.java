package records.transformations.function.text;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import utility.ValueFunction;

public class StringJoinWith extends FunctionDefinition
{
    public StringJoinWith() throws InternalException
    {
        super("text:join text with");
    }

    @Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes)
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            @Value Object[] args = Utility.castTuple(param, 2);
            
            @Value ListEx textList = Utility.cast(args[0], ListEx.class);
            String separator = Utility.cast(args[1], String.class);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < textList.size(); i++)
            {
                if (i > 0)
                    b.append(separator);
                b.append(Utility.cast(textList.get(i), String.class));
            }
            return DataTypeUtility.value(b.toString());
        }
    }
}