package test;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Sort;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeThat;

/**
 * Created by neil on 16/11/2016.
 */
public class GenSort extends Generator<Sort>
{
    public GenSort()
    {
        super(Sort.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Sort generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Pair<TableId, TableId> ids = TestUtil.generateTableIdPair(sourceOfRandomness);
        List<ColumnId> cols = TestUtil.makeList(sourceOfRandomness, 1, 10, () -> TestUtil.generateColumnId(sourceOfRandomness));

        try
        {
            return new Sort(new DummyManager(), ids.getFirst(), ids.getSecond(), cols);
        }
        catch (UserException | InternalException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }
}
