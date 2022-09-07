package test.gen;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import records.gui.grid.CellSelection;
import records.gui.grid.GridArea;
import xyz.columnal.styled.StyledString;
import test.gen.GenGridAreaList.GridAreaList;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.FXPlatformRunnable;

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
        int length = sourceOfRandomness.nextInt(2, 40);
        ImmutableList.Builder<GridArea> r = ImmutableList.builder();
        for (int i = 0; i < length; i++)
        {
            int x0 = sourceOfRandomness.nextInt(1, 25);
            int x1 = x0 + sourceOfRandomness.nextInt(1, 15);
            int y0 = sourceOfRandomness.nextInt(1, 25);
            int y1 = y0 + sourceOfRandomness.nextInt(1, 15);
            GridArea gridArea = makeGridArea(x0, y0, x1, y1);
            r.add(gridArea);
        }
        return new GridAreaList(r.build());
    }

    @OnThread(Tag.FXPlatform)
    public static GridArea makeGridArea(int x0, int y0, int x1, int y1)
    {
        GridArea gridArea = new GridArea()
        {
            @Override
            protected @OnThread(Tag.FXPlatform) void updateKnownRows(int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
            {
                
            }

            @Override
            protected CellPosition recalculateBottomRightIncl()
            {
                return getPosition().offsetByRowCols(x1 - x0, y1 - y0);
            }
            
            @Override
            public @Nullable CellSelection getSelectionForSingleCell(CellPosition cellPosition)
            {
                return null;
            }

            @Override
            public String getSortKey()
            {
                return "";
            }
        };
        gridArea.setPosition(new CellPosition(CellPosition.row(y0), CellPosition.col(x0)));
        return gridArea;
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
