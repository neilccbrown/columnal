package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

import java.util.List;

/**
 * Created by neil on 13/12/2016.
 */
public class GenUnit extends Generator<Unit>
{
    private @MonotonicNonNull List<SingleUnit> units;

    public GenUnit()
    {
        super(Unit.class);
    }

    @Override
    public Unit generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        try
        {
            if (units == null)
            {

                    UnitManager mgr = new UnitManager();
                    units = mgr.getAllDeclared();

            }

            Unit u = Unit.SCALAR;
            int numUnits = r.nextInt(0, 5);
            for (int i = 0; i < numUnits; i++)
            {
                int power = r.nextInt(1, 10);
                if (r.nextBoolean())
                    power = -power;

                u = u.times(new Unit(r.choose(units)).raisedTo(power));
            }
            return u;
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}