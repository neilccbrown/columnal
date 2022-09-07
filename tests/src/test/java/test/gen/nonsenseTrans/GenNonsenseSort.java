package test.gen.nonsenseTrans;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.DataTestUtil;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.TableId;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.Sort;
import xyz.columnal.transformations.Sort.Direction;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeThat;

/**
 * Created by neil on 16/11/2016.
 */
public class GenNonsenseSort extends Generator<Transformation_Mgr>
{
    public GenNonsenseSort()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Pair<TableId, TableId> ids = TestUtil.generateTableIdPair(sourceOfRandomness);
        ImmutableList<Pair<ColumnId, Direction>> cols = DataTestUtil.makeList(sourceOfRandomness, 1, 10, () -> new Pair<>(TestUtil.generateColumnId(sourceOfRandomness), sourceOfRandomness.nextBoolean() ? Direction.ASCENDING : Direction.DESCENDING));

        try
        {
            DummyManager mgr = new DummyManager();
            return new Transformation_Mgr(mgr, new Sort(mgr, new InitialLoadDetails(ids.getFirst(), null, null, null), ids.getSecond(), cols));
        }
        catch (InternalException | UserException e)
        {
            assumeNoException(e);
            throw new RuntimeException(e);
        }
    }
}