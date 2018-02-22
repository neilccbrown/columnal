package test.gen;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.CellPosition;
import records.data.Table.MessageWhenEmpty;
import records.gui.grid.GridArea;
import styled.StyledString;
import test.gen.GenGridAreaList.GridAreaList;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;

public class GenGridAreaList extends Generator<GridAreaList>
{
    @SuppressWarnings("unchecked")
    public GenGridAreaList()
    {
        super(GridAreaList.class);
    }
    
    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public GridAreaList generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        int length = sourceOfRandomness.nextInt(2, 30);
        ImmutableList.Builder<GridArea> r = ImmutableList.builder();
        for (int i = 0; i < length; i++)
        {
            int x0 = sourceOfRandomness.nextInt(1, 25);
            int x1 = x0 + sourceOfRandomness.nextInt(1, 15);
            int y0 = sourceOfRandomness.nextInt(1, 25);
            int y1 = y0 + sourceOfRandomness.nextInt(1, 15);
            GridArea gridArea = new GridArea(new MessageWhenEmpty(StyledString.s("")))
            {
                @Override
                protected @OnThread(Tag.FXPlatform) void updateKnownRows(int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
                {
                    
                }

                @Override
                public int getColumnCount()
                {
                    return x1 - x0 + 1;
                }

                @Override
                public int getCurrentKnownRows()
                {
                    return y1 - y0 + 1;
                }
            };
            gridArea.setPosition(new CellPosition(y0, x0));
            r.add(gridArea);
        }
        return new GridAreaList(r.build());
    }
    
    public static class GridAreaList
    {
        public final ImmutableList<GridArea> gridAreas;

        private GridAreaList(ImmutableList<GridArea> gridAreas)
        {
            this.gridAreas = gridAreas;
        }
    }
}
