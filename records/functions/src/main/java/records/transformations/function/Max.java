package records.transformations.function;

import annotation.qual.Value;
import records.error.InternalException;
import records.error.UserException;
import records.types.MutVar;
import records.types.TypeCons;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.ListEx;

public class Max extends FunctionDefinition
{
    public Max()
    {
        super("max", Instance::new, Max::listOfAny);
    }

    public static FunctionGroup group()
    {
        return new FunctionGroup("max.short", new Max());
    }
    
    private static FunctionTypes listOfAny()
    {
        TypeExp any = new MutVar(null);
        return new FunctionTypes(any, new TypeCons(null, TypeExp.CONS_LIST, any));
    }    

    private static class Instance extends FunctionInstance
    {
        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            @Value ListEx list = Utility.cast(param, ListEx.class);
            if (list.size() == 0)
            {
                throw new UserException("Cannot take minimum of empty list");
            }
            else
            {
                @Value Object max = list.get(0);
                for (int i = 1; i < list.size(); i++)
                {
                    @Value Object val = list.get(i);
                    if (Utility.compareValues(max, val) < 0)
                        max = val;
                }
                return max;
            }
        }
    }
}
