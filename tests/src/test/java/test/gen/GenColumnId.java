package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.TableId;
import test.TestUtil;

public class GenColumnId extends Generator<ColumnId>
{
    public GenColumnId()
    {
        super(ColumnId.class);
    }

    @Override
    public ColumnId generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return TestUtil.generateColumnId(sourceOfRandomness);
    }
}
