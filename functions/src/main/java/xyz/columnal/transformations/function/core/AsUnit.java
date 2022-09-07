package xyz.columnal.transformations.function.core;

import annotation.qual.Value;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.math.BigDecimal;
import java.math.MathContext;

public class AsUnit extends FunctionDefinition
{
    public AsUnit() throws InternalException
    {
        super("core:convert unit");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes)
    {
        try
        {
            Unit dest = paramTypes.apply("u").getLeft("Variable u should be unit but was type");
            Unit orig = paramTypes.apply("v").getLeft("Variable v should be unit but was type");

            Pair<Rational, Unit> destToCanon = typeManager.getUnitManager().canonicalise(dest);
            Pair<Rational, Unit> origToCanon = typeManager.getUnitManager().canonicalise(orig);
            
            if (!destToCanon.getSecond().equals(origToCanon.getSecond()))
            {
                throw new UserException("No mapping from " + orig + " to " + dest + " because " + orig + " reduces to " + origToCanon.getSecond() + " whereas " + dest + " reduces to " + destToCanon.getSecond());
            }
            
            return new Instance(origToCanon.getFirst().times(destToCanon.getFirst().reciprocal()));
        }
        catch (InternalException | UserException e)
        {
            return new ValueFunction()
            {
                @Override
                public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
                {
                    throw e;
                }
            };
        }
    }

    private static class Instance extends ValueFunction
    {
        private final @Value BigDecimal scaleFactor;

        private Instance(Rational scaleFactor)
        {
            this.scaleFactor = DataTypeUtility.value(new BigDecimal(scaleFactor.getNum()).divide(new BigDecimal(scaleFactor.getDen()), MathContext.DECIMAL128));
        }

        @Override
        public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
        {
            @Value Number src = arg(1, Number.class);
            return Utility.multiplyNumbers(src, scaleFactor);
        }
    }
}