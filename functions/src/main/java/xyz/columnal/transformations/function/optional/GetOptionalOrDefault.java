package xyz.columnal.transformations.function.optional;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.ValueFunction1;
import xyz.columnal.transformations.function.ValueFunction2;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.TaggedValue;

public class GetOptionalOrDefault extends FunctionDefinition
{

    public static final @FuncDocKey String NAME = "optional:get optional or";

    public GetOptionalOrDefault() throws InternalException
    {
        super(NAME);
    }
    
    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction2<TaggedValue, Object>(TaggedValue.class, Object.class) {
            @Override
            public @OnThread(Tag.Simulation) @Value Object call2(@Value TaggedValue taggedValue, @Value Object defaultValue) throws InternalException, UserException
            {
                if (taggedValue.getTagIndex() == 1 && taggedValue.getInner() != null)
                    return taggedValue.getInner();
                else
                    return defaultValue;
            }
        };
    }
}